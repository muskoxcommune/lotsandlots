package io.lotsandlots.etrade.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * https://apisb.etrade.com/docs/api/market/api-quote-v1.html#/definitions/QuoteResponse
 */
public class QuoteResponse {

    @JsonProperty("QuoteData")
    private List<QuoteData> quoteDataList;

    public List<QuoteData> getQuoteDataList() {
        return quoteDataList;
    }
    public void setQuoteDataList(List<QuoteData> quoteDataList) {
        this.quoteDataList = quoteDataList;
    }

    public static class QuoteData {

        @JsonProperty("All")
        private AllQuoteDetails allQuoteDetails;

        @JsonProperty("dateTimeUTC")
        private Long dateTime;

        private String quoteStatus;

        public AllQuoteDetails getAllQuoteDetails() {
            return allQuoteDetails;
        }
        public void setAllQuoteDetails(AllQuoteDetails allQuoteDetails) {
            this.allQuoteDetails = allQuoteDetails;
        }

        public Long getDateTime() {
            return dateTime;
        }
        public void setDateTime(Long dateTime) {
            this.dateTime = dateTime;
        }

        public String getQuoteStatus() {
            return quoteStatus;
        }
        public void setQuoteStatus(String quoteStatus) {
            this.quoteStatus = quoteStatus;
        }
    }

    public static class AllQuoteDetails {

        private Float lastTrade;

        public Float getLastTrade() {
            return lastTrade;
        }
        public void setLastTrade(Float lastTrade) {
            this.lastTrade = lastTrade;
        }
    }
}
