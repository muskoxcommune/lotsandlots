package io.lotsandlots.etrade;

import io.lotsandlots.etrade.api.ApiConfig;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public abstract class EtradeDataFetcher implements EtradeOAuthClient, Runnable {

    private static final EtradeRestTemplateFactory DEFAULT_REST_TEMPLATE_FACTORY = EtradeRestTemplateFactory.getTemplateFactory();
    private static final ScheduledExecutorService DEFAULT_SCHEDULED_EXECUTOR = Executors.newSingleThreadScheduledExecutor();
    protected static final ApiConfig API = EtradeRestTemplateFactory.getTemplateFactory().getApiConfig();

    private EtradeRestTemplateFactory restTemplateFactory = DEFAULT_REST_TEMPLATE_FACTORY;
    private ScheduledExecutorService scheduledExecutor = DEFAULT_SCHEDULED_EXECUTOR;

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
