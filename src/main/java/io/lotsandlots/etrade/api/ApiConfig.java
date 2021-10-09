package io.lotsandlots.etrade.api;

public class ApiConfig {

    private String accountIdKey;
    private String accountListUrl;
    private String baseUrl;
    private String ordersQueryString;
    private String ordersUrl;
    private String portfolioQueryString;
    private String portfolioUrl;

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
}
