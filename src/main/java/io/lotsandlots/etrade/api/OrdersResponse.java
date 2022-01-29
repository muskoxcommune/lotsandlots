package io.lotsandlots.etrade.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class OrdersResponse {

    Long marker;
    String next;
    @JsonProperty("Order")
    List<Order> orderList;

    public Long getMarker() {
        return marker;
    }
    public boolean hasMarker() {
        return marker != null;
    }
    public void setMarker(Long marker) {
        this.marker = marker;
    }

    public String getNext() {
        return next;
    }
    public void setNext(String next) {
        this.next = next;
    }

    public List<Order> getOrderList() {
        return orderList;
    }
    public void setOrderList(List<Order> orderList) {
        this.orderList = orderList;
    }

    public static class Order {

        String details;

        @JsonProperty("OrderDetail")
        List<OrderDetail> orderDetailList;

        Long orderId;
        String orderType;

        public String getDetails() {
            return details;
        }
        public void setDetails(String details) {
            this.details = details;
        }

        public List<OrderDetail> getOrderDetailList() {
            return orderDetailList;
        }
        public void setOrderDetailList(List<OrderDetail> orderDetailList) {
            this.orderDetailList = orderDetailList;
        }

        public Long getOrderId() {
            return orderId;
        }
        public void setOrderId(Long orderId) {
            this.orderId = orderId;
        }

        public String getOrderType() {
            return orderType;
        }
        public void setOrderType(String orderType) {
            this.orderType = orderType;
        }
    }
}
