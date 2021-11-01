package io.lotsandlots.etrade.api;

public class CancelOrderResponse {

    Long accountId;
    Long cancelTime;
    Long orderId;

    public Long getAccountId() {
        return accountId;
    }
    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public Long getCancelTime() {
        return cancelTime;
    }
    public void setCancelTime(Long cancelTime) {
        this.cancelTime = cancelTime;
    }

    public Long getOrderId() {
        return orderId;
    }
    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
}
