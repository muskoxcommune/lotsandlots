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
        Float filledQuantity; // From Instrument. Not a part of E*Trade's response
        Float limitPrice; // From OrderDetail. Not a part of E*Trade's response
        String orderAction; // From Instrument. Not a part of E*Trade's response

        @JsonProperty("OrderDetail")
        List<OrderDetail> orderDetailList;

        Long orderId;
        Long orderedQuantity; // From Instrument. Not a part of E*Trade's response
        String orderType;
        Float orderValue; // From OrderDetail. Not a part of E*Trade's response
        Long placedTime; // From OrderDetail. Not a part of E*Trade's response
        String status; // From OrderDetail. Not a part of E*Trade's response
        String symbol; // From Instrument. Not a part of E*Trade's response


        public String getDetails() {
            return details;
        }
        public void setDetails(String details) {
            this.details = details;
        }

        public Float getFilledQuantity() {
            return filledQuantity;
        }
        public void setFilledQuantity(Float filledQuantity) {
            this.filledQuantity = filledQuantity;
        }

        public Float getLimitPrice() {
            return limitPrice;
        }
        public void setLimitPrice(Float limitPrice) {
            this.limitPrice = limitPrice;
        }

        public String getOrderAction() {
            return orderAction;
        }
        public void setOrderAction(String orderAction) {
            this.orderAction = orderAction;
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

        public Long getOrderedQuantity() {
            return orderedQuantity;
        }
        public void setOrderedQuantity(Long orderedQuantity) {
            this.orderedQuantity = orderedQuantity;
        }

        public String getOrderType() {
            return orderType;
        }
        public void setOrderType(String orderType) {
            this.orderType = orderType;
        }

        public Float getOrderValue() {
            return orderValue;
        }
        public void setOrderValue(Float orderValue) {
            this.orderValue = orderValue;
        }

        public Long getPlacedTime() {
            return placedTime;
        }
        public void setPlacedTime(Long placedTime) {
            this.placedTime = placedTime;
        }

        public String getStatus() {
            return status;
        }
        public void setStatus(String status) {
            this.status = status;
        }

        public String getSymbol() {
            return symbol;
        }
        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }
    }
}
