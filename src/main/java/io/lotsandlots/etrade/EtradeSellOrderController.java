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

    private boolean cancelAllOrdersOnLotsOrdersMismatch = true;
    private final ExecutorService executor;
    private final EtradeOrdersDataFetcher ordersDataFetcher;
    private final EtradePortfolioDataFetcher portfolioDataFetcher;
    private final List<String> sellOrderDisabledSymbols = new LinkedList<>();

    public EtradeSellOrderController(EtradePortfolioDataFetcher portfolioDataFetcher,
                                     EtradeOrdersDataFetcher ordersDataFetcher) {
        this(portfolioDataFetcher, ordersDataFetcher, DEFAULT_EXECUTOR);
    }

    public EtradeSellOrderController(EtradePortfolioDataFetcher portfolioDataFetcher,
                                     EtradeOrdersDataFetcher ordersDataFetcher,
                                     ExecutorService executor) {
        if (CONFIG.hasPath("etrade.cancelAllOrdersOnLotsOrdersMismatch")) {
            cancelAllOrdersOnLotsOrdersMismatch = CONFIG.getBoolean("etrade.cancelAllOrdersOnLotsOrdersMismatch");
        }
        if (CONFIG.hasPath("etrade.disableSellOrderCreation")) {
            sellOrderDisabledSymbols.addAll(CONFIG.getStringList("etrade.disableSellOrderCreation"));
        }

        this.executor = executor;
        this.ordersDataFetcher = ordersDataFetcher;
        this.portfolioDataFetcher = portfolioDataFetcher;

        portfolioDataFetcher.addSymbolToLotsIndexPutHandler(this);

        LOG.info("Initialized EtradeSellOrderCreator, cancelAllOrdersOnLotsOrdersMismatch={} sellOrderDisabledSymbols={}",
                cancelAllOrdersOnLotsOrdersMismatch, sellOrderDisabledSymbols
        );
    }

    @Override
    public void handleSymbolToLotsIndexPut(String symbol,
                                           List<PositionLotsResponse.PositionLot> lots,
                                           PortfolioResponse.Totals totals) {
        if (isSellOrderCreationDisabled(symbol)) {
            LOG.debug("Skipping disabled sell order creation feature, symbol={}", symbol);
        } else {
            Long lastSuccessfulFetchTimeMillis = ordersDataFetcher.getLastSuccessfulFetchTimeMillis();
            if (lastSuccessfulFetchTimeMillis == null) {
                LOG.debug("Skipping sell order creation, orders data fetch has not occurred, symbol={}", symbol);
                return;
            }
            long currentTimeMillis = System.currentTimeMillis();
            long deltaMillis = currentTimeMillis - lastSuccessfulFetchTimeMillis;
            long thresholdMillis = ordersDataFetcher.getOrdersDataExpirationSeconds() * 1000L;
            if (deltaMillis > thresholdMillis) {
                LOG.warn("Skipping sell order creation due to orders data staleness, "
                                + "lastSuccessfulFetchTimeMillis={} deltaMillis={} thresholdMillis={} symbol={}",
                        lastSuccessfulFetchTimeMillis, deltaMillis, thresholdMillis, symbol);
            } else {
                executor.submit(new SymbolToLotsIndexPutEventRunnable(symbol, lots));
            }
        }
    }

    boolean isSellOrderCreationDisabled(String symbol) {
        return sellOrderDisabledSymbols.contains(symbol);
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
            ordersDataFetcher.getOrderCache().invalidate(orderId);
            ordersDataFetcher.indexOrdersBySymbol();
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
                if (!cancelAllOrdersOnLotsOrdersMismatch) {
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