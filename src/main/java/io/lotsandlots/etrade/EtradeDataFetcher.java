package io.lotsandlots.etrade;

import io.lotsandlots.etrade.api.ApiConfig;
import io.lotsandlots.etrade.oauth.EtradeOAuthClient;
import io.lotsandlots.etrade.rest.EtradeRestTemplateFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public abstract class EtradeDataFetcher implements EtradeOAuthClient, Runnable {

    private static final ApiConfig DEFAULT_API = EtradeRestTemplateFactory.getTemplateFactory().getApiConfig();
    private static final EtradeRestTemplateFactory DEFAULT_REST_TEMPLATE_FACTORY = EtradeRestTemplateFactory.getTemplateFactory();
    private static final ScheduledExecutorService DEFAULT_SCHEDULED_EXECUTOR = Executors.newSingleThreadScheduledExecutor();

    private ApiConfig apiConfig = DEFAULT_API;
    private Long lastFailedFetchTimeMillis = null;
    private Long lastSuccessfulFetchTimeMillis = null;
    private EtradeRestTemplateFactory restTemplateFactory = DEFAULT_REST_TEMPLATE_FACTORY;
    private ScheduledExecutorService scheduledExecutor = DEFAULT_SCHEDULED_EXECUTOR;

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

    ScheduledExecutorService getScheduledExecutor() {
        return scheduledExecutor;
    }
    void setScheduledExecutor(ScheduledExecutorService scheduledExecutor) {
        this.scheduledExecutor = scheduledExecutor;
    }
}
