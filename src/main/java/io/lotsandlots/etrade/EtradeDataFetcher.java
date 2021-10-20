package io.lotsandlots.etrade;

import io.lotsandlots.etrade.api.ApiConfig;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public abstract class EtradeDataFetcher implements EtradeApiClient, Runnable {

    protected static final ApiConfig API = EtradeRestTemplateFactory.getClient().getApiConfig();
    protected static final EtradeRestTemplateFactory REST_TEMPLATE_FACTORY = EtradeRestTemplateFactory.getClient();
    protected static final ScheduledExecutorService SCHEDULED_EXECUTOR = Executors.newSingleThreadScheduledExecutor();

    public static void destroy() {
        SCHEDULED_EXECUTOR.shutdown();
    }
}
