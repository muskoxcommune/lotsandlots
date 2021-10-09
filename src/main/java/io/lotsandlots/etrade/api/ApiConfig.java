package io.lotsandlots.etrade.api;

public class ApiConfig {

    private String acctListUri;
    private String baseUrl;
    private String ordersQueryString;
    private String ordersUri;
    private String portfolioQueryString;
    private String portfolioUri;

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

    public String getOrdersQueryString() {
        return ordersQueryString;
    }
    public void setOrdersQueryString(String ordersQueryString) {
        this.ordersQueryString = ordersQueryString;
    }

    public String getOrdersUri() {
        return ordersUri;
    }
    public void setOrdersUri(String ordersUri) {
        this.ordersUri = ordersUri;
    }

    public String getPortfolioQueryString() {
        return portfolioQueryString;
    }
    public void setPortfolioQueryString(String portfolioQueryString) {
        this.portfolioQueryString = portfolioQueryString;
    }

    public String getPortfolioUri() {
        return portfolioUri;
    }
    public void setPortfolioUri(String portfolioUri) {
        this.portfolioUri = portfolioUri;
    }
}
