package io.lotsandlots.etrade.model;

public class Order {

    Float limitPrice;
    String orderAction;
    Long orderId;
    Long orderedQuantity;
    Float orderValue;
    Long placedTime;
    String status;
    String symbol;

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

    public void setOrderValue(Float orderValue) {
        this.orderValue = orderValue;
    }
    public Float getOrderValue() {
        return orderValue;
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
