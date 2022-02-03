package io.lotsandlots.etrade;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lotsandlots.etrade.api.OrderDetail;
import io.lotsandlots.etrade.api.PlaceOrderRequest;
import io.lotsandlots.etrade.api.PlaceOrderResponse;
import io.lotsandlots.etrade.api.PreviewOrderRequest;
import io.lotsandlots.etrade.api.PreviewOrderResponse;
import io.lotsandlots.etrade.model.Order;
import io.lotsandlots.etrade.oauth.SecurityContext;
import io.lotsandlots.etrade.rest.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

public abstract class EtradeOrderCreator extends EtradeDataFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(EtradeOrderCreator.class);

    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    PreviewOrderRequest newPreviewOrderRequest(String clientOrderId,
                                               OrderDetail orderDetail)
            throws JsonProcessingException {
        PreviewOrderRequest previewOrderRequest = new PreviewOrderRequest();
        previewOrderRequest.setOrderDetailList(orderDetail);
        previewOrderRequest.setOrderType("EQ");
        previewOrderRequest.setClientOrderId(clientOrderId);
        if (LOG.isDebugEnabled()) {
            LOG.debug("PreviewOrderRequest{}", OBJECT_MAPPER.writeValueAsString(previewOrderRequest));
        }
        return previewOrderRequest;
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
                    OrderDetail orderDetail)
            throws GeneralSecurityException, JsonProcessingException, UnsupportedEncodingException {

        PreviewOrderRequest previewOrderRequest = newPreviewOrderRequest(
                clientOrderId, orderDetail);
        PreviewOrderResponse previewOrderResponse = fetchPreviewOrderResponse(
                securityContext, previewOrderRequest);
        int previewIdListSize = previewOrderResponse.getPreviewIdList().size();
        if (previewIdListSize != 1) {
            throw new RuntimeException("Expected 1 previewId, got " + previewIdListSize);
        }

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
        OrderDetail placeOrderResponseOrderDetail = placeOrderResponse.getOrderDetailList().get(0);
        OrderDetail.Instrument instrument = placeOrderResponseOrderDetail.getInstrumentList().get(0);

        Order order = new Order();
        order.setLimitPrice(placeOrderResponseOrderDetail.getLimitPrice());
        order.setOrderAction(instrument.getOrderAction());
        order.setOrderId(placeOrderResponse.getOrderIdList().get(0).getOrderId());
        order.setOrderedQuantity(instrument.getQuantity());
        order.setPlacedTime(placeOrderResponse.getPlacedTime());
        order.setStatus("OPEN");
        order.setSymbol(previewOrderRequest.getOrderDetailList().get(0)
                                           .getInstrumentList().get(0)
                                           .getProduct()
                                           .getSymbol());
        EtradeOrdersDataFetcher.putOrderInCache(order);
        EtradeOrdersDataFetcher.refreshSymbolToOrdersIndexes();
    }
}
