package io.lotsandlots.etrade.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public class PreviewOrderRequest {

    private String clientOrderId = UUID.randomUUID().toString();

    @JsonProperty("order")
    private List<OrdersResponse.OrderDetail> orderDetailList;

    private String orderType;

    public String getClientOrderId() {
        return clientOrderId;
    }
    public void setClientOrderId(String clientOrderId) {
        this.clientOrderId = clientOrderId;
    }

    public List<OrdersResponse.OrderDetail> getOrderDetailList() {
        return orderDetailList;
    }
    public void setOrderDetailList(List<OrdersResponse.OrderDetail> orderDetailList) {
        this.orderDetailList = orderDetailList;
    }

    public String getOrderType() {
        return orderType;
    }
    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }
}
