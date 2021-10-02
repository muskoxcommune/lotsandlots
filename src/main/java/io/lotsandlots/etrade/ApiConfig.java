package io.lotsandlots.etrade;

public class ApiConfig {

    private String acctListUri;
    private String balanceUri;
    private String baseUrl;
    private String orderListUri;
    private String orderPreviewUri;
    private String portfolioUri;
    private String queryParam;
    private String quoteUri;

    public String getAcctListUri() {
        return acctListUri;
    }
    public void setAcctListUri(String acctListUri) {
        this.acctListUri = acctListUri;
    }

    public String getBalanceUri() {
        return balanceUri;
    }
    public void setBalanceUri(String balanceUri) {
        this.balanceUri = balanceUri;
    }

    public String getBaseUrl() {
        return baseUrl;
    }
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getOrderListUri() {
        return orderListUri;
    }
    public void setOrderListUri(String orderListUri) {
        this.orderListUri = orderListUri;
    }

    public String getOrderPreviewUri() {
        return orderPreviewUri;
    }
    public void setOrderPreviewUri(String orderPreviewUri) {
        this.orderPreviewUri = orderPreviewUri;
    }

    public String getPortfolioUri() {
        return portfolioUri;
    }
    public void setPortfolioUri(String portfolioUri) {
        this.portfolioUri = portfolioUri;
    }

    public String getQueryParam() {
        return queryParam;
    }
    public void setQueryParam(String queryParam) {
        this.queryParam = queryParam;
    }

    public String getQuoteUri() {
        return quoteUri;
    }
    public void setQuoteUri(String quoteUri) {
        this.quoteUri = quoteUri;
    }
}
