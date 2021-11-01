package io.lotsandlots.etrade.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class PlaceOrderResponse {

    @JsonProperty("Order")
    List<OrderDetail> orderDetailList;
    @JsonProperty("OrderIds")
    List<OrderId> orderIdList;
    Long placedTime;

    public List<OrderDetail> getOrderDetailList() {
        return orderDetailList;
    }
    public void setOrderDetailList(List<OrderDetail> orderDetailList) {
        this.orderDetailList = orderDetailList;
    }

    public List<OrderId> getOrderIdList() {
        return orderIdList;
    }
    public void setOrderIdList(List<OrderId> orderIdList) {
        this.orderIdList = orderIdList;
    }

    public Long getPlacedTime() {
        return placedTime;
    }
    public void setPlacedTime(Long placedTime) {
        this.placedTime = placedTime;
    }

    public static class OrderId {

        Long orderId;

        public Long getOrderId() {
            return orderId;
        }
        public void setOrderId(Long orderId) {
            this.orderId = orderId;
        }
    }
}
