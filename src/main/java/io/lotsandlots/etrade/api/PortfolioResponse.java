package io.lotsandlots.etrade.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigInteger;
import java.util.List;

public class PortfolioResponse {

    @JsonProperty("Totals")
    private Totals totals;

    @JsonProperty("AccountPortfolio")
    private List<AccountPortfolio> accountPortfolioList;

    public Totals getTotals() {
        return totals;
    }
    public void setTotals(Totals totals) {
        this.totals = totals;
    }

    public AccountPortfolio getAccountPortfolio() {
        return accountPortfolioList.get(0);
    }
    public List<AccountPortfolio> getAccountPortfolioList() {
        return accountPortfolioList;
    }
    public void setAccountPortfolioList(List<AccountPortfolio> accountPortfolioList) {
        this.accountPortfolioList = accountPortfolioList;
    }

    public static class Totals {

        private Float cashBalance;
        private Float todaysGainLoss;
        private Float todaysGainLossPct;
        private Float totalGainLoss;
        private Float totalGainLossPct;
        private Float totalMarketValue;
        private Float totalPricePaid;

        public Float getCashBalance() {
            return cashBalance;
        }
        public void setCashBalance(Float cashBalance) {
            this.cashBalance = cashBalance;
        }

        public Float getTodaysGainLoss() {
            return todaysGainLoss;
        }
        public void setTodaysGainLoss(Float todaysGainLoss) {
            this.todaysGainLoss = todaysGainLoss;
        }

        public Float getTodaysGainLossPct() {
            return todaysGainLossPct;
        }
        public void setTodaysGainLossPct(Float todaysGainLossPct) {
            this.todaysGainLossPct = todaysGainLossPct;
        }

        public Float getTotalGainLoss() {
            return totalGainLoss;
        }
        public void setTotalGainLoss(Float totalGainLoss) {
            this.totalGainLoss = totalGainLoss;
        }

        public Float getTotalGainLossPct() {
            return totalGainLossPct;
        }
        public void setTotalGainLossPct(Float totalGainLossPct) {
            this.totalGainLossPct = totalGainLossPct;
        }

        public Float getTotalMarketValue() {
            return totalMarketValue;
        }
        public void setTotalMarketValue(Float totalMarketValue) {
            this.totalMarketValue = totalMarketValue;
        }

        public Float getTotalPricePaid() {
            return totalPricePaid;
        }
        public void setTotalPricePaid(Float totalPricePaid) {
            this.totalPricePaid = totalPricePaid;
        }
    }

    public static class AccountPortfolio {

        private String nextPageNo;

        @JsonProperty("Position")
        private List<Position> positionList;

        private Integer totalPages;

        public String getNextPageNo() {
            return nextPageNo;
        }
        public boolean hasNextPageNo() {
            return nextPageNo != null;
        }
        public void setNextPageNo(String next) {
            this.nextPageNo = next;
        }

        public List<Position> getPositionList() {
            return positionList;
        }
        public int positionListSize() {
            return positionList.size();
        }
        public void setPositionList(List<Position> positionList) {
            this.positionList = positionList;
        }

        public Integer getTotalPages() {
            return totalPages;
        }
        public void setTotalPages(Integer totalPages) {
            this.totalPages = totalPages;
        }

        @Override
        public String toString() {
            return "AccountPortfolio{"
                    + "nextPageNo: " + nextPageNo + ", "
                    + "positionListSize: " + positionListSize() + ", "
                    + "totalPages: " + totalPages
                    + "}";
        }
    }

    public static class Position {

        private Float costPerShare;
        private BigInteger dateAcquired;
        private Float daysGain;
        private Float daysGainPct;
        private String lotsDetails;
        private Float marketValue;
        private Float pctOfPortfolio;
        private BigInteger positionId;
        private String positionType;
        private Float pricePaid;
        private Integer quantity;
        private String quoteDetails;
        private String symbolDescription;
        private Float totalCost;
        private Float totalGain;
        private Float totalGainPct;

        public Float getCostPerShare() {
            return costPerShare;
        }
        public void setCostPerShare(Float costPerShare) {
            this.costPerShare = costPerShare;
        }

        public BigInteger getDateAcquired() {
            return dateAcquired;
        }
        public void setDateAcquired(BigInteger dateAcquired) {
            this.dateAcquired = dateAcquired;
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

        public String getLotsDetails() {
            return lotsDetails;
        }
        public void setLotsDetails(String lotsDetails) {
            this.lotsDetails = lotsDetails;
        }

        public Float getMarketValue() {
            return marketValue;
        }
        public void setMarketValue(Float marketValue) {
            this.marketValue = marketValue;
        }

        public Float getPctOfPortfolio() {
            return pctOfPortfolio;
        }
        public void setPctOfPortfolio(Float pctOfPortfolio) {
            this.pctOfPortfolio = pctOfPortfolio;
        }

        public BigInteger getPositionId() {
            return positionId;
        }
        public void setPositionId(BigInteger positionId) {
            this.positionId = positionId;
        }

        public String getPositionType() {
            return positionType;
        }
        public void setPositionType(String positionType) {
            this.positionType = positionType;
        }

        public Float getPricePaid() {
            return pricePaid;
        }
        public void setPricePaid(Float pricePaid) {
            this.pricePaid = pricePaid;
        }

        public Integer getQuantity() {
            return quantity;
        }
        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public String getQuoteDetails() {
            return quoteDetails;
        }
        public void setQuoteDetails(String quoteDetails) {
            this.quoteDetails = quoteDetails;
        }

        public String getSymbolDescription() {
            return symbolDescription;
        }
        public void setSymbolDescription(String symbolDescription) {
            this.symbolDescription = symbolDescription;
        }

        public Float getTotalCost() {
            return totalCost;
        }
        public void setTotalCost(Float totalCost) {
            this.totalCost = totalCost;
        }

        public Float getTotalGain() {
            return totalGain;
        }
        public void setTotalGain(Float totalGain) {
            this.totalGain = totalGain;
        }

        public Float getTotalGainPct() {
            return totalGainPct;
        }
        public void setTotalGainPct(Float totalGainPct) {
            this.totalGainPct = totalGainPct;
        }
    }
}
