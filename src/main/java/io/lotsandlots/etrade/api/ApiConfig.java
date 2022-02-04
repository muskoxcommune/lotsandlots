package io.lotsandlots.etrade.api;

public class ApiConfig {

    private String accountIdKey;
    private String accountListUrl;
    private String baseUrl;
    private String ordersCancelUrl;
    private String ordersPlaceUrl;
    private String ordersPreviewUrl;
    private String ordersQueryString;
    private String ordersUrl;
    private String portfolioQueryString;
    private String portfolioUrl;
    private String quoteUrl;

    public String getAccountIdKey() {
        return accountIdKey;
    }
    public void setAccountIdKey(String accountIdKey) {
        this.accountIdKey = accountIdKey;
    }

    public String getAccountListUrl() {
        return accountListUrl;
    }
    public void setAccountListUrl(String accountListUrl) {
        this.accountListUrl = accountListUrl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getOrdersCancelUrl() {
        return ordersCancelUrl;
    }
    public void setOrdersCancelUrl(String ordersCancelUrl) {
        this.ordersCancelUrl = ordersCancelUrl;
    }

    public String getOrdersPlaceUrl() {
        return ordersPlaceUrl;
    }
    public void setOrdersPlaceUrl(String ordersPlaceUrl) {
        this.ordersPlaceUrl = ordersPlaceUrl;
    }

    public String getOrdersPreviewUrl() {
        return ordersPreviewUrl;
    }
    public void setOrdersPreviewUrl(String ordersPreviewUrl) {
        this.ordersPreviewUrl = ordersPreviewUrl;
    }

    public String getOrdersQueryString() {
        return ordersQueryString;
    }
    public void setOrdersQueryString(String ordersQueryString) {
        this.ordersQueryString = ordersQueryString;
    }

    public String getOrdersUrl() {
        return ordersUrl;
    }
    public void setOrdersUrl(String ordersUrl) {
        this.ordersUrl = ordersUrl;
    }

    public String getPortfolioQueryString() {
        return portfolioQueryString;
    }
    public void setPortfolioQueryString(String portfolioQueryString) {
        this.portfolioQueryString = portfolioQueryString;
    }

    public String getPortfolioUrl() {
        return portfolioUrl;
    }
    public void setPortfolioUrl(String portfolioUrl) {
        this.portfolioUrl = portfolioUrl;
    }

    public String getQuoteUrl() {
        return quoteUrl;
    }
    public void setQuoteUrl(String quoteUrl) {
        this.quoteUrl = quoteUrl;
    }
}
