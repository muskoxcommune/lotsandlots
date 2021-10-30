package io.lotsandlots.etrade.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * https://apisb.etrade.com/docs/api/order/api-order-v1.html#/definitions/PreviewOrderRequest
 */
public class PreviewOrderRequest {

    private String clientOrderId;
    @JsonProperty("Order")
    private List<OrderDetail> orderDetailList;
    private String orderType;

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
    public void setOrderDetailList(OrderDetail orderDetail) {
        this.orderDetailList = new ArrayList<>();
        this.orderDetailList.add(orderDetail);
    }

    public String getOrderType() {
        return orderType;
    }
    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }
}
