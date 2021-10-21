package io.lotsandlots.etrade.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class PositionLotsResponse {

    @JsonProperty("PositionLot")
    List<PositionLot> positionLots;

    public List<PositionLot> getPositionLots() {
        return positionLots;
    }
    public void setPositionLots(List<PositionLot> positionLots) {
        this.positionLots = positionLots;
    }

    public static class PositionLot {

        Long acquiredDate;
        Float availableQty;
        Float daysGain;
        Float daysGainPct;
        Integer legNo;
        Float marketValue;
        Integer orderNo;
        Float originalQty;
        Long positionId;
        Long positionLotId;
        Float positionPctOfPortfolio; // From position. Not a part of E*Trade's response
        Float price;
        Float remainingQty;
        String symbol; // Not a part of E*Trade's response
        Float targetPrice; // Not a part of E*Trade's response
        Integer termCode;
        Float totalCost;
        Float totalCostForGainPct;
        Float totalGain;
        Integer totalLotCount; // Total number of lots for position. Not a part of E*Trade's response
        Float totalPositionCost; // From position. Not a part of E*Trade's response

        public Long getAcquiredDate() {
            return acquiredDate;
        }
        public void setAcquiredDate(Long acquiredDate) {
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

        public Long getPositionId() {
            return positionId;
        }
        public void setPositionId(Long positionId) {
            this.positionId = positionId;
        }

        public Long getPositionLotId() {
            return positionLotId;
        }
        public void setPositionLotId(Long positionLotId) {
            this.positionLotId = positionLotId;
        }

        public Float getPositionPctOfPortfolio() {
            return positionPctOfPortfolio;
        }
        public void setPositionPctOfPortfolio(Float positionPctOfPortfolio) {
            this.positionPctOfPortfolio = positionPctOfPortfolio;
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

        public String getSymbol() {
            return symbol;
        }
        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }

        public Float getTargetPrice() {
            return targetPrice;
        }
        public void setTargetPrice(Float targetPrice) {
            this.targetPrice = targetPrice;
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

        public Integer getTotalLotCount() {
            return totalLotCount;
        }
        public void setTotalLotCount(Integer totalLotCount) {
            this.totalLotCount = totalLotCount;
        }

        public Float getTotalPositionCost() {
            return totalPositionCost;
        }
        public void setTotalPositionCost(Float totalPositionCost) {
            this.totalPositionCost = totalPositionCost;
        }
    }
}
