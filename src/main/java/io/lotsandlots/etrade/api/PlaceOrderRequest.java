package io.lotsandlots.etrade.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class PlaceOrderRequest {

    String clientOrderId;
    @JsonProperty("Order")
    List<OrderDetail> orderDetailList;
    String orderType;
    @JsonProperty("PreviewIds")
    List<PreviewOrderResponse.PreviewId> previewIdList;

    public String getClientOrderId() {
        return clientOrderId;
    }
    public void setClientOrderId(String clientOrderId) {
        this.clientOrderId = clientOrderId;
    }

    public List<OrderDetail> getOrderDetailList() {
        return orderDetailList;
    }
    public void setOrderDetailList(List<OrderDetail> orderDetailList) {
        this.orderDetailList = orderDetailList;
    }

    public String getOrderType() {
        return orderType;
    }
    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public List<PreviewOrderResponse.PreviewId> getPreviewIdList() {
        return previewIdList;
    }
    public void setPreviewIdList(List<PreviewOrderResponse.PreviewId> previewIdList) {
        this.previewIdList = previewIdList;
    }
}
