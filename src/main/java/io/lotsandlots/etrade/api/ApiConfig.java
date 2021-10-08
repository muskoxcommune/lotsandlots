package io.lotsandlots.etrade.api;

public class ApiConfig {

    private String acctListUri;
    private String baseUrl;
    private String ordersUri;
    private String portfolioUri;
    private String portfolioQueryString;

    public String getAcctListUri() {
        return acctListUri;
    }
    public void setAcctListUri(String acctListUri) {
        this.acctListUri = acctListUri;
    }

    public String getBaseUrl() {
        return baseUrl;
    }
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getOrdersUri() {
        return ordersUri;
    }
    public void setOrdersUri(String ordersUri) {
        this.ordersUri = ordersUri;
    }

    public String getPortfolioUri() {
        return portfolioUri;
    }
    public void setPortfolioUri(String portfolioUri) {
        this.portfolioUri = portfolioUri;
    }

    public String getPortfolioQueryString() {
        return portfolioQueryString;
    }
    public void setPortfolioQueryString(String portfolioQueryString) {
        this.portfolioQueryString = portfolioQueryString;
    }
}
