package io.lotsandlots.etrade;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.typesafe.config.Config;
import io.lotsandlots.etrade.api.CancelOrderRequest;
import io.lotsandlots.etrade.api.CancelOrderResponse;
import io.lotsandlots.etrade.api.OrderDetail;
import io.lotsandlots.etrade.api.PlaceOrderRequest;
import io.lotsandlots.etrade.api.PlaceOrderResponse;
import io.lotsandlots.etrade.api.PortfolioResponse;
import io.lotsandlots.etrade.api.PositionLotsResponse;
import io.lotsandlots.etrade.api.PreviewOrderRequest;
import io.lotsandlots.etrade.api.PreviewOrderResponse;
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

    public static final Boolean CANCEL_ALL_ORDERS_ON_LOTS_ORDERS_MISMATCH = CONFIG.getBoolean(
            "etrade.cancelAllOrdersOnLotsOrdersMismatch");

    private ExecutorService executor;

    public EtradeSellOrderCreator() {
        executor = DEFAULT_EXECUTOR;
        LOG.info("Initialized EtradeSellOrderCreator, cancelAllOrdersOnLotsOrdersMismatch={}",
                CANCEL_ALL_ORDERS_ON_LOTS_ORDERS_MISMATCH
        );
    }

    @Override
    public void handleSymbolToLotsIndexPut(String symbol,
                                           List<PositionLotsResponse.PositionLot> lots,
                                           PortfolioResponse.Totals totals) {
        if (CONFIG.getStringList("etrade.skipSellOrderCreation").contains(symbol)) {
            LOG.debug("Skipping sell order creation, symbol={}", symbol);
        } else {
            executor.submit(new SymbolToLotsIndexPutEvent(symbol, lots));
        }
    }

    @VisibleForTesting
    void setExecutor(ExecutorService executor) {
        this.executor = executor;
    }

    static class SymbolToLotsIndexPutEvent extends EtradeDataFetcher {

        private List<PositionLotsResponse.PositionLot> lotList;
        private final String symbol;

        SymbolToLotsIndexPutEvent(String symbol,
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

        PreviewOrderResponse fetchPreviewOrderResponse(SecurityContext securityContext,
                                                       PreviewOrderRequest previewOrderRequest)
                throws GeneralSecurityException, JsonProcessingException, UnsupportedEncodingException {

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
            } else if (LOG.isDebugEnabled()) {
                LOG.debug("PreviewOrderResponse{}", OBJECT_MAPPER.writeValueAsString(previewOrderResponse));
            }
            return previewOrderResponse;
        }

        void placeOrder(SecurityContext securityContext,
                        String clientOrderId,
                        PreviewOrderRequest previewOrderRequest,
                        PreviewOrderResponse previewOrderResponse)
                throws GeneralSecurityException, JsonProcessingException, UnsupportedEncodingException {
            PlaceOrderRequest placeOrderRequest = new PlaceOrderRequest();
            placeOrderRequest.setClientOrderId(clientOrderId);
            placeOrderRequest.setOrderDetailList(previewOrderRequest.getOrderDetailList());
            placeOrderRequest.setOrderType("EQ");
            placeOrderRequest.setPreviewIdList(previewOrderResponse.getPreviewIdList());
            if (LOG.isDebugEnabled()) {
                LOG.debug("PlaceOrderRequest{}", OBJECT_MAPPER.writeValueAsString(placeOrderRequest));
            }
            Map<String, PlaceOrderRequest> payload = new HashMap<>();
            payload.put("PlaceOrderRequest", placeOrderRequest);

            Message orderPlaceMessage = new Message();
            orderPlaceMessage.setRequiresOauth(true);
            orderPlaceMessage.setHttpMethod("POST");
            orderPlaceMessage.setContentType(MediaType.APPLICATION_JSON_VALUE);
            orderPlaceMessage.setUrl(getApiConfig().getOrdersPlaceUrl());
            setOAuthHeader(securityContext, orderPlaceMessage);

            ResponseEntity<PlaceOrderResponse> placeOrderResponseEntity =
                    getRestTemplateFactory()
                            .newCustomRestTemplate()
                            .doPost(orderPlaceMessage,
                                    OBJECT_MAPPER.writeValueAsString(payload),
                                    PlaceOrderResponse.class);
            PlaceOrderResponse placeOrderResponse = placeOrderResponseEntity.getBody();
            if (placeOrderResponse == null) {
                throw new RuntimeException("Empty place order response");
            } else if (LOG.isDebugEnabled()) {
                LOG.debug("PlaceOrderResponse{}", OBJECT_MAPPER.writeValueAsString(placeOrderResponse));
            }

            OrderDetail orderDetail = placeOrderResponse.getOrderDetailList().get(0);
            OrderDetail.Instrument instrument = orderDetail.getInstrumentList().get(0);
            Order order = new Order();
            order.setLimitPrice(orderDetail.getLimitPrice());
            order.setOrderAction(instrument.getOrderAction());
            order.setOrderId(placeOrderResponse.getOrderIdList().get(0).getOrderId());
            order.setOrderValue(orderDetail.getLimitPrice() * instrument.getQuantity());
            order.setOrderedQuantity(instrument.getQuantity());
            order.setPlacedTime(placeOrderResponse.getPlacedTime());
            order.setStatus("OPEN");
            order.setSymbol(symbol);
            EtradeOrdersDataFetcher.putOrderInCache(order);
            EtradeOrdersDataFetcher.refreshSymbolToOrdersIndexes();
        }

        PreviewOrderRequest previewOrderRequestFromPositionLot(PositionLotsResponse.PositionLot positionLot,
                                                               String clientOrderId)
                throws JsonProcessingException {
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

            PreviewOrderRequest previewOrderRequest = new PreviewOrderRequest();
            previewOrderRequest.setOrderDetailList(orderDetail);
            previewOrderRequest.setOrderType("EQ");
            previewOrderRequest.setClientOrderId(clientOrderId);
            if (LOG.isDebugEnabled()) {
                LOG.debug("PreviewOrderRequest{}", OBJECT_MAPPER.writeValueAsString(previewOrderRequest));
            }
            return previewOrderRequest;
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
            Map<String, List<Order>> symbolToOrdersIndex =
                    EtradeOrdersDataFetcher.getDataFetcher().getSymbolToSellOrdersIndex();
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
                    PreviewOrderRequest previewOrderRequest = previewOrderRequestFromPositionLot(
                            positionLot, clientOrderId);
                    PreviewOrderResponse previewOrderResponse = fetchPreviewOrderResponse(
                            securityContext, previewOrderRequest);
                    int previewIdListSize = previewOrderResponse.getPreviewIdList().size();
                    if (previewIdListSize != 1) {
                        throw new RuntimeException("Expected 1 previewId, got " + previewIdListSize);
                    }
                    placeOrder(securityContext, clientOrderId, previewOrderRequest, previewOrderResponse);
                }
            } catch (Exception e) {
                LOG.debug("Unable to finish creating sell orders, symbol={}", symbol, e);
            }
        }
    }
}