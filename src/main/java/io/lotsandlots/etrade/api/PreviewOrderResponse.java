package io.lotsandlots.etrade.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * https://apisb.etrade.com/docs/api/order/api-order-v1.html#/definitions/PreviewOrderResponse
 */
public class PreviewOrderResponse {

    String accountId;
    String clientOrderId;
    String commissionMessage;
    @JsonProperty("dstFlag")
    Boolean dayLightSavingsTimeFlag;
    @JsonProperty("marginLevelCd")
    String marginLevelCode;
    @JsonProperty("Order")
    List<OrderDetail> orderDetailList;
    String orderType;
    @JsonProperty("PreviewIds")
    List<PreviewId> previewIdList;
    Long previewTime;
    Float totalOrderValue;
    Float totalCommission;

    public String getAccountId() {
        return accountId;
    }
    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getClientOrderId() {
        return clientOrderId;
    }
    public void setClientOrderId(String clientOrderId) {
        this.clientOrderId = clientOrderId;
    }

    public String getCommissionMessage() {
        return commissionMessage;
    }
    public void setCommissionMessage(String commissionMessage) {
        this.commissionMessage = commissionMessage;
    }

    public Boolean getDayLightSavingsTimeFlag() {
        return dayLightSavingsTimeFlag;
    }
    public void setDayLightSavingsTimeFlag(Boolean dayLightSavingsTimeFlag) {
        this.dayLightSavingsTimeFlag = dayLightSavingsTimeFlag;
    }

    public String getMarginLevelCode() {
        return marginLevelCode;
    }
    public void setMarginLevelCode(String marginLevelCode) {
        this.marginLevelCode = marginLevelCode;
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

    public List<PreviewId> getPreviewIdList() {
        return previewIdList;
    }
    public void setPreviewIdList(List<PreviewId> previewIdList) {
        this.previewIdList = previewIdList;
    }

    public Long getPreviewTime() {
        return previewTime;
    }
    public void setPreviewTime(Long previewTime) {
        this.previewTime = previewTime;
    }

    public Float getTotalCommission() {
        return totalCommission;
    }
    public void setTotalCommission(Float totalCommission) {
        this.totalCommission = totalCommission;
    }

    public Float getTotalOrderValue() {
        return totalOrderValue;
    }
    public void setTotalOrderValue(Float totalOrderValue) {
        this.totalOrderValue = totalOrderValue;
    }

    public static class PreviewId {

        private Long previewId;
        private String cashMargin;

        public Long getPreviewId() {
            return previewId;
        }
        public void setPreviewId(Long previewId) {
            this.previewId = previewId;
        }

        public String getCashMargin() {
            return cashMargin;
        }
        public void setCashMargin(String cashMargin) {
            this.cashMargin = cashMargin;
        }
    }
}
