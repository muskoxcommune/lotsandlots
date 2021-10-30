package io.lotsandlots.etrade;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import io.lotsandlots.etrade.api.OrderDetail;
import io.lotsandlots.etrade.api.OrdersResponse;
import io.lotsandlots.etrade.api.PortfolioResponse;
import io.lotsandlots.etrade.api.PositionLotsResponse;
import io.lotsandlots.etrade.api.PreviewOrderRequest;
import io.lotsandlots.etrade.api.PreviewOrderResponse;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EtradeSellOrderCreator implements EtradePortfolioDataFetcher.SymbolToLotsIndexPutHandler {

    private static final Config CONFIG = ConfigWrapper.getConfig();
    private static final ExecutorService DEFAULT_EXECUTOR = Executors.newFixedThreadPool(5);
    private static final Logger LOG = LoggerFactory.getLogger(EtradeSellOrderCreator.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private static final Boolean CANCEL_ALL_ORDERS_ON_LOTS_ORDERS_MISMATCH = CONFIG.getBoolean(
            "etrade.cancelAllOrdersOnLotsOrdersMismatch");

    private ExecutorService executor;

    public EtradeSellOrderCreator() {
        executor = DEFAULT_EXECUTOR;
        LOG.info("Initialized EtradeSellOrderCreator");
    }

    @Override
    public void handleSymbolToLotsIndexPut(String symbol,
                                           List<PositionLotsResponse.PositionLot> lots,
                                           PortfolioResponse.Totals totals) {
        executor.submit(new SymbolToLotsIndexPutEvent(symbol, lots));
    }

    static class SymbolToLotsIndexPutEvent extends EtradeDataFetcher {

        private List<PositionLotsResponse.PositionLot> lotList;
        private final String symbol;

        SymbolToLotsIndexPutEvent(String symbol,
                                  List<PositionLotsResponse.PositionLot> lotList) {
            this.lotList = lotList;
            this.symbol = symbol;
        }

        PreviewOrderResponse fetchPreviewOrderResponse(SecurityContext securityContext,
                                                       PositionLotsResponse.PositionLot positionLot)
                throws GeneralSecurityException, JsonProcessingException, UnsupportedEncodingException {
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

            String clientOrderId = UUID.randomUUID().toString().substring(0, 8);
            PreviewOrderRequest previewOrderRequest = new PreviewOrderRequest();
            previewOrderRequest.setOrderDetailList(orderDetail);
            previewOrderRequest.setOrderType("EQ");
            previewOrderRequest.setClientOrderId(clientOrderId);
            Map<String, PreviewOrderRequest> payload = new HashMap<>();
            payload.put("PreviewOrderRequest", previewOrderRequest);

            Message ordersPreviewMessage = new Message();
            ordersPreviewMessage.setRequiresOauth(true);
            ordersPreviewMessage.setHttpMethod("POST");
            ordersPreviewMessage.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ordersPreviewMessage.setUrl(getApiConfig().getOrdersPreviewUrl());
            setOAuthHeader(securityContext, ordersPreviewMessage);

            ResponseEntity<PreviewOrderResponse> previewOrderResponseEntity =
                    getRestTemplateFactory()
                            .newCustomRestTemplate()
                            .doPost(ordersPreviewMessage,
                                    OBJECT_MAPPER.writeValueAsString(payload),
                                    PreviewOrderResponse.class);
            PreviewOrderResponse previewOrderResponse =
                    previewOrderResponseEntity.getBody();
            if (previewOrderResponse == null) {
                throw new RuntimeException("Empty preview order response");
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("OrderPreviewResponse{}", OBJECT_MAPPER.writeValueAsString(previewOrderResponse));
                }
                if (!clientOrderId.equals(previewOrderResponse.getClientOrderId())) {
                    throw new RuntimeException("Mismatched clientOrderId");
                }
            }
            return previewOrderResponse;
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
            Map<String, List<OrdersResponse.Order>> symbolToOrdersIndex =
                    EtradeOrdersDataFetcher.getSymbolToSellOrdersIndex();
            if (symbolToOrdersIndex.containsKey(symbol)) {
                List<OrdersResponse.Order> orderList = symbolToOrdersIndex.get(symbol);
                int orderListSize = orderList.size();
                LOG.debug("Found {} orders for {} lots, symbol={}", orderListSize, lotListSize, symbol);
                if (orderListSize == lotListSize) {
                    return;
                }
                if (!CANCEL_ALL_ORDERS_ON_LOTS_ORDERS_MISMATCH) {
                    return;
                }
                // Cancel all existing orders
                LOG.info("Canceling {} existing sell orders, symbol={}", orderListSize, symbol);
                for (OrdersResponse.Order order : orderList) {
                    try {
                        Message orderCancelMessage = new Message();
                        orderCancelMessage.setRequiresOauth(true);
                        orderCancelMessage.setHttpMethod("PUT");
                        orderCancelMessage.setUrl(getApiConfig().getOrdersCancelUrl());
                        setOAuthHeader(securityContext, orderCancelMessage);
                    } catch (Exception e) {
                    }
                }
            } else {
                LOG.debug("Found 0 orders for {} lots, symbol={}", lotListSize, symbol);
            }
            // Create orders, then update local caches immediately
            LOG.info("Creating sell orders for {} lots, symbol={}", lotListSize, symbol);
            try {
                for (PositionLotsResponse.PositionLot positionLot : lotList) {
                    PreviewOrderResponse previewOrderResponse = fetchPreviewOrderResponse(
                            securityContext, positionLot);
                    // Place order.
                }
            } catch (Exception e) {
                LOG.debug("Unable to finish creating sell orders, symbol={}", symbol, e);
            }
        }
    }
}