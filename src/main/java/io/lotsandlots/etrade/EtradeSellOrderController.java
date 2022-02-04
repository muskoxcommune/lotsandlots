package io.lotsandlots.etrade;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.typesafe.config.Config;
import io.lotsandlots.etrade.api.CancelOrderRequest;
import io.lotsandlots.etrade.api.CancelOrderResponse;
import io.lotsandlots.etrade.api.OrderDetail;
import io.lotsandlots.etrade.api.PortfolioResponse;
import io.lotsandlots.etrade.api.PositionLotsResponse;
import io.lotsandlots.etrade.model.Order;
import io.lotsandlots.etrade.oauth.SecurityContext;
import io.lotsandlots.etrade.rest.Message;
import io.lotsandlots.util.ConfigWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EtradeSellOrderController implements EtradePortfolioDataFetcher.SymbolToLotsIndexPutHandler {

    private static final Config CONFIG = ConfigWrapper.getConfig();
    private static final ExecutorService DEFAULT_EXECUTOR = Executors.newFixedThreadPool(5);
    private static final Logger LOG = LoggerFactory.getLogger(EtradeSellOrderController.class);

    public static final Boolean CANCEL_ALL_ORDERS_ON_LOTS_ORDERS_MISMATCH = CONFIG.getBoolean(
            "etrade.cancelAllOrdersOnLotsOrdersMismatch");

    private final List<String> sellOrderDisabledSymbols = new LinkedList<>();

    private EtradeOrdersDataFetcher ordersDataFetcher = EtradeOrdersDataFetcher.getDataFetcher();
    private ExecutorService executor;

    public EtradeSellOrderController() {
        executor = DEFAULT_EXECUTOR;
        if (CONFIG.hasPath("etrade.disableSellOrderCreation")) {
            sellOrderDisabledSymbols.addAll(CONFIG.getStringList("etrade.disableSellOrderCreation"));
        }
        LOG.info("Initialized EtradeSellOrderCreator, cancelAllOrdersOnLotsOrdersMismatch={}",
                CANCEL_ALL_ORDERS_ON_LOTS_ORDERS_MISMATCH
        );
    }

    @Override
    public void handleSymbolToLotsIndexPut(String symbol,
                                           List<PositionLotsResponse.PositionLot> lots,
                                           PortfolioResponse.Totals totals) {
        if (isSellOrderCreationDisabled(symbol)) {
            LOG.debug("Skipping sell order creation, symbol={}", symbol);
        } else {
            executor.submit(new SymbolToLotsIndexPutEventRunnable(symbol, lots));
        }
    }

    boolean isSellOrderCreationDisabled(String symbol) {
        return sellOrderDisabledSymbols.contains(symbol);
    }

    void setExecutor(ExecutorService executor) {
        this.executor = executor;
    }

    void setOrdersDataFetcher(EtradeOrdersDataFetcher ordersDataFetcher) {
        this.ordersDataFetcher = ordersDataFetcher;
    }

    SymbolToLotsIndexPutEventRunnable newSymbolToLotsIndexPutEventRunnable(
            String symbol, List<PositionLotsResponse.PositionLot> lotList) {
        return new SymbolToLotsIndexPutEventRunnable(symbol, lotList);
    }

    class SymbolToLotsIndexPutEventRunnable extends EtradeOrderCreator {

        private List<PositionLotsResponse.PositionLot> lotList;
        private final String symbol;

        SymbolToLotsIndexPutEventRunnable(String symbol,
                                          List<PositionLotsResponse.PositionLot> lotList) {
            this.lotList = lotList;
            this.symbol = symbol;
        }

        void cancelOrder(SecurityContext securityContext, Long orderId)
                throws GeneralSecurityException, JsonProcessingException, UnsupportedEncodingException {
            CancelOrderRequest cancelOrderRequest = new CancelOrderRequest();
            cancelOrderRequest.setOrderId(orderId);
            if (LOG.isDebugEnabled()) {
                LOG.debug("CancelOrderRequest{}", OBJECT_MAPPER.writeValueAsString(cancelOrderRequest));
            }
            Map<String, CancelOrderRequest> payload = new HashMap<>();
            payload.put("CancelOrderRequest", cancelOrderRequest);

            Message orderCancelMessage = new Message();
            orderCancelMessage.setRequiresOauth(true);
            orderCancelMessage.setHttpMethod("PUT");
            orderCancelMessage.setContentType(MediaType.APPLICATION_JSON_VALUE);
            orderCancelMessage.setUrl(getApiConfig().getOrdersCancelUrl());
            setOAuthHeader(securityContext, orderCancelMessage);
            ResponseEntity<CancelOrderResponse> cancelOrderResponseEntity =
                    getRestTemplateFactory()
                            .newCustomRestTemplate()
                            .doPut(orderCancelMessage,
                                   OBJECT_MAPPER.writeValueAsString(payload),
                                   CancelOrderResponse.class);
            CancelOrderResponse cancelOrderResponse = cancelOrderResponseEntity.getBody();
            if (cancelOrderResponse == null) {
                throw new RuntimeException("Empty cancel order response");
            } else if (LOG.isDebugEnabled()) {
                LOG.debug("CancelOrderResponse{}", OBJECT_MAPPER.writeValueAsString(cancelOrderResponse));
            }
            EtradeOrdersDataFetcher.removeOrderFromCache(orderId);
            EtradeOrdersDataFetcher.refreshSymbolToOrdersIndexes();
        }

        @Override
        public void run() {
            SecurityContext securityContext = getRestTemplateFactory().getSecurityContext();
            if (!securityContext.isInitialized()) {
                LOG.warn("SecurityContext not initialized, please go to /etrade/authorize");
                return;
            }
            if (getApiConfig().getOrdersPreviewUrl() == null) {
                LOG.warn("Please configure etrade.accountIdKey");
                return;
            }
            int lotListSize = lotList.size();
            Map<String, List<Order>> symbolToOrdersIndex = ordersDataFetcher.getSymbolToSellOrdersIndex();
            if (symbolToOrdersIndex.containsKey(symbol)) {
                List<Order> orderList = symbolToOrdersIndex.get(symbol);
                int orderListSize = orderList.size();
                LOG.debug("Found {} sell orders for {} lots, symbol={}", orderListSize, lotListSize, symbol);
                if (orderListSize == lotListSize) {
                    return;
                }
                if (!CANCEL_ALL_ORDERS_ON_LOTS_ORDERS_MISMATCH) {
                    return;
                }
                LOG.info("Canceling {} existing sell orders, symbol={}", orderListSize, symbol);
                try {
                    for (Order order : orderList) {
                        cancelOrder(securityContext, order.getOrderId());
                    }
                } catch (Exception e) {
                    LOG.debug("Failed to cancel all sell orders, symbol={}", symbol, e);
                    return;
                }
            } else {
                LOG.debug("Found 0 sell orders for {} lots, symbol={}", lotListSize, symbol);
            }
            // Create orders, then update local caches immediately
            LOG.info("Creating sell orders for {} lots, symbol={}", lotListSize, symbol);
            try {
                for (PositionLotsResponse.PositionLot positionLot : lotList) {
                    String clientOrderId = UUID.randomUUID().toString().substring(0, 8);

                    OrderDetail.Product product = new OrderDetail.Product();
                    product.setSecurityType("EQ");
                    product.setSymbol(positionLot.getSymbol());

                    OrderDetail.Lots instrumentLots = new OrderDetail.Lots();
                    instrumentLots.newLotList(positionLot.getPositionLotId(), positionLot.getRemainingQty().longValue());

                    OrderDetail.Instrument instrument = new OrderDetail.Instrument();
                    instrument.setLots(instrumentLots);
                    instrument.setOrderAction("SELL");
                    instrument.setProduct(product);
                    instrument.setQuantity(positionLot.getRemainingQty().longValue());
                    instrument.setQuantityType("QUANTITY");

                    OrderDetail orderDetail = new OrderDetail();
                    orderDetail.setAllOrNone(false);
                    orderDetail.newInstrumentList(instrument);
                    orderDetail.setOrderTerm("GOOD_UNTIL_CANCEL");
                    orderDetail.setMarketSession("REGULAR");
                    orderDetail.setPriceType("LIMIT");
                    orderDetail.setLimitPrice(
                            BigDecimal.valueOf(positionLot.getTargetPrice())
                                    .setScale(2, RoundingMode.HALF_UP)
                                    .floatValue());
                    placeOrder(securityContext, clientOrderId, orderDetail);
                }
            } catch (Exception e) {
                LOG.debug("Unable to finish creating sell orders, symbol={}", symbol, e);
            }
        }
    }
}