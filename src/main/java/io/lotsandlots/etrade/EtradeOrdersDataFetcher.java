package io.lotsandlots.etrade;

import io.lotsandlots.etrade.api.ApiConfig;
import io.lotsandlots.etrade.api.OrdersResponse;
import io.lotsandlots.etrade.oauth.SecurityContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EtradeOrdersDataFetcher implements EtradeApiClient, Runnable {

    private static final ApiConfig API = EtradeRestTemplateFactory.getClient().getApiConfig();
    private static final Logger LOG = LoggerFactory.getLogger(EtradeOrdersDataFetcher.class);
    private static final ScheduledExecutorService SCHEDULED_EXECUTOR = Executors.newSingleThreadScheduledExecutor();

    private static EtradeOrdersDataFetcher DATA_FETCHER = null;

    private EtradeOrdersDataFetcher () {
        SCHEDULED_EXECUTOR.scheduleAtFixedRate(this, 0, 60, TimeUnit.SECONDS);
    }

    public static void destroy() {
        SCHEDULED_EXECUTOR.shutdown();
    }

    private void fetchOrdersResponse(SecurityContext securityContext,
                                     String marker)
            throws GeneralSecurityException, UnsupportedEncodingException {
        Message ordersMessage = new Message();
        ordersMessage.setRequiresOauth(true);
        ordersMessage.setHttpMethod("GET");
        ordersMessage.setUrl(API.getOrdersUrl());
        String ordersQueryString = API.getOrdersQueryString();
        if (!StringUtils.isBlank(marker)) {
            ordersQueryString += "&marker=" + marker;
        }
        ordersMessage.setQueryString(ordersQueryString);
        setOAuthHeader(securityContext, ordersMessage);
        ResponseEntity<OrdersResponse> ordersResponseResponseEntity = EtradeRestTemplateFactory
                .getClient()
                .newCustomRestTemplate()
                .execute(ordersMessage, OrdersResponse.class);
        OrdersResponse ordersResponse = ordersResponseResponseEntity.getBody();
        if (ordersResponse == null) {
            throw new RuntimeException("Empty response");
        } else {
            // Cache data

            if (ordersResponse.hasMarker()) {
                fetchOrdersResponse(securityContext, ordersResponse.getMarker().toString());
            }
        }
    }

    public static void init() {
        if (DATA_FETCHER == null) {
            DATA_FETCHER = new EtradeOrdersDataFetcher();
        }
    }

    @Override
    public void run() {
        SecurityContext securityContext = EtradeRestTemplateFactory.getClient().getSecurityContext();
        if (!securityContext.isInitialized()) {
            LOG.warn("SecurityContext not initialized, please go to /etrade/authorize");
            return;
        }
        if (API.getOrdersUrl() == null) {
            LOG.warn("Please configure etrade.accountIdKey");
            return;
        }
        long timeStartedMillis = System.currentTimeMillis();
        try {
            fetchOrdersResponse(securityContext, null);
            LOG.info("Fetched orders data, duration={}ms",
                    System.currentTimeMillis() - timeStartedMillis);
        } catch (Exception e) {
            LOG.info("Failed to fetch orders data, duration={}ms",
                    System.currentTimeMillis() - timeStartedMillis, e);
        }
    }
}
