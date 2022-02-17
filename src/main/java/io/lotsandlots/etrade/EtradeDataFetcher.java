package io.lotsandlots.etrade;

import io.lotsandlots.etrade.api.ApiConfig;
import io.lotsandlots.etrade.oauth.EtradeOAuthClient;
import io.lotsandlots.etrade.rest.EtradeRestTemplateFactory;

public abstract class EtradeDataFetcher implements EtradeOAuthClient, Runnable {

    private static final ApiConfig API = EtradeRestTemplateFactory.getTemplateFactory().getApiConfig();
    private static final EtradeRestTemplateFactory REST_TEMPLATE_FACTORY = EtradeRestTemplateFactory.getTemplateFactory();

    private ApiConfig apiConfig = API;
    private Long lastFailedFetchTimeMillis = null;
    private Long lastSuccessfulFetchTimeMillis = null;
    private EtradeRestTemplateFactory restTemplateFactory = REST_TEMPLATE_FACTORY;

    ApiConfig getApiConfig() {
        return apiConfig;
    }
    void setApiConfig(ApiConfig apiConfig) {
        this.apiConfig = apiConfig;
    }

    public Long getLastFailedFetchTimeMillis() {
        return lastFailedFetchTimeMillis;
    }
    public void setLastFailedFetchTimeMillis(Long lastFailedFetchTimeMillis) {
        this.lastFailedFetchTimeMillis = lastFailedFetchTimeMillis;
    }

    public Long getLastSuccessfulFetchTimeMillis() {
        return lastSuccessfulFetchTimeMillis;
    }
    public void setLastSuccessfulFetchTimeMillis(Long lastSuccessfulFetchTimeMillis) {
        this.lastSuccessfulFetchTimeMillis = lastSuccessfulFetchTimeMillis;
    }

    EtradeRestTemplateFactory getRestTemplateFactory() {
        return restTemplateFactory;
    }
    void setRestTemplateFactory(EtradeRestTemplateFactory restTemplateFactory) {
        this.restTemplateFactory = restTemplateFactory;
    }
}
