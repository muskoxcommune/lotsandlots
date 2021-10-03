package io.lotsandlots.etrade;

public class ApiConfig {

    private String acctListUri;
    private String baseUrl;
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
