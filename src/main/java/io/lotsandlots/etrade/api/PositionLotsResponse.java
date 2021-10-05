package io.lotsandlots.etrade.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigInteger;
import java.util.List;

public class PositionLotsResponse {

    @JsonProperty("PositionLot")
    List<PositionLot> positionLots;

    public List<PositionLot> getPositionLots() {
        return positionLots;
    }

    public static class PositionLot {

        BigInteger acquiredDate;
        Float availableQty;
        Float daysGain;
        Float daysGainPct;
        Integer legNo;
        Float marketValue;
        Integer orderNo;
        Float originalQty;
        BigInteger positionId;
        BigInteger positionLotId;
        Float price;
        Float remainingQty;
        Integer termCode;
        Float totalCost;
        Float totalCostForGainPct;
        Float totalGain;

        public BigInteger getAcquiredDate() {
            return acquiredDate;
        }
        public void setAcquiredDate(BigInteger acquiredDate) {
            this.acquiredDate = acquiredDate;
        }

        public Float getAvailableQty() {
            return availableQty;
        }
        public void setAvailableQty(Float availableQty) {
            this.availableQty = availableQty;
        }

        public Float getDaysGain() {
            return daysGain;
        }
        public void setDaysGain(Float daysGain) {
            this.daysGain = daysGain;
        }

        public Float getDaysGainPct() {
            return daysGainPct;
        }
        public void setDaysGainPct(Float daysGainPct) {
            this.daysGainPct = daysGainPct;
        }

        public Integer getLegNo() {
            return legNo;
        }
        public void setLegNo(Integer legNo) {
            this.legNo = legNo;
        }

        public Float getMarketValue() {
            return marketValue;
        }
        public void setMarketValue(Float marketValue) {
            this.marketValue = marketValue;
        }

        public Integer getOrderNo() {
            return orderNo;
        }
        public void setOrderNo(Integer orderNo) {
            this.orderNo = orderNo;
        }

        public Float getOriginalQty() {
            return originalQty;
        }
        public void setOriginalQty(Float originalQty) {
            this.originalQty = originalQty;
        }

        public BigInteger getPositionId() {
            return positionId;
        }
        public void setPositionId(BigInteger positionId) {
            this.positionId = positionId;
        }

        public BigInteger getPositionLotId() {
            return positionLotId;
        }
        public void setPositionLotId(BigInteger positionLotId) {
            this.positionLotId = positionLotId;
        }

        public Float getPrice() {
            return price;
        }
        public void setPrice(Float price) {
            this.price = price;
        }

        public Float getRemainingQty() {
            return remainingQty;
        }
        public void setRemainingQty(Float remainingQty) {
            this.remainingQty = remainingQty;
        }

        public Integer getTermCode() {
            return termCode;
        }
        public void setTermCode(Integer termCode) {
            this.termCode = termCode;
        }

        public Float getTotalCost() {
            return totalCost;
        }
        public void setTotalCost(Float totalCost) {
            this.totalCost = totalCost;
        }

        public Float getTotalCostForGainPct() {
            return totalCostForGainPct;
        }
        public void setTotalCostForGainPct(Float totalCostForGainPct) {
            this.totalCostForGainPct = totalCostForGainPct;
        }

        public Float getTotalGain() {
            return totalGain;
        }
        public void setTotalGain(Float totalGain) {
            this.totalGain = totalGain;
        }
    }
}
