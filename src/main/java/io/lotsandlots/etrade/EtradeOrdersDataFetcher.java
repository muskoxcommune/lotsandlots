package io.lotsandlots.etrade;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.lotsandlots.etrade.api.ApiConfig;
import io.lotsandlots.etrade.api.OrdersResponse;
import io.lotsandlots.etrade.oauth.SecurityContext;
import io.lotsandlots.util.DateFormatter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EtradeOrdersDataFetcher implements EtradeApiClient, Runnable {

    private static final ApiConfig API = EtradeRestTemplateFactory.getClient().getApiConfig();
    private static final Logger LOG = LoggerFactory.getLogger(EtradeOrdersDataFetcher.class);
    private static final Cache<String, List<OrdersResponse.Order>> SELL_ORDERS = CacheBuilder
            .newBuilder()
            .expireAfterWrite(90, TimeUnit.SECONDS)
            .build();

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

        long currentTimeMillis = System.currentTimeMillis();
        // 60 seconds * 60 minutes * 24 hours * 180 days = 15552000 seconds
        ordersQueryString += "&fromDate=" + DateFormatter.epochSecondsToDateString(
                (currentTimeMillis  / 1000L) - 15552000, "MMddyyyy");
        ordersQueryString += "&toDate=" + DateFormatter.epochSecondsToDateString(
                currentTimeMillis / 1000L, "MMddyyyy");

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
            for (OrdersResponse.Order order : ordersResponse.getOrderList()) {
                List<OrdersResponse.OrderDetail> orderDetails = order.getOrderDetailList();
                if (orderDetails.size() == 1) {
                    OrdersResponse.OrderDetail orderDetail = orderDetails.get(0);
                    if (!orderDetail.getStatus().equals("OPEN") && !orderDetail.getStatus().equals("PARTIAL")) {
                        continue;
                    }
                    List<OrdersResponse.OrderDetail.Instrument> instruments = orderDetail.getInstrumentList();
                    if (instruments.size() == 1) {
                        OrdersResponse.OrderDetail.Instrument instrument = instruments.get(0);
                        if (instrument.getOrderAction().equals("BUY") || instrument.getOrderAction().equals("SELL")) {
                            String symbol = instrument.getProduct().getSymbol();
                            order.setFilledQuantity(instrument.getFilledQuantity());
                            order.setLimitPrice(orderDetail.getLimitPrice());
                            order.setOrderAction(instrument.getOrderAction());
                            order.setOrderedQuantity(instrument.getOrderedQuantity());
                            order.setOrderValue(orderDetail.getOrderValue());
                            order.setPlacedTime(orderDetail.getPlacedTime());
                            order.setStatus(orderDetail.getStatus());
                            order.setSymbol(symbol);
                            List<OrdersResponse.Order> orderList = SELL_ORDERS.getIfPresent(symbol);
                            if (orderList == null) {
                                List<OrdersResponse.Order> newOrderList = new LinkedList<>();
                                newOrderList.add(order);
                                SELL_ORDERS.put(symbol, newOrderList);
                            } else {
                                orderList.add(order);
                            }
                        }
                    } else {
                        LOG.warn("Expected OrderDetail to include one Instrument");
                    }
                } else {
                    LOG.warn("Expected Order to include one OrderDetail");
                }
            }
            if (ordersResponse.hasMarker()) {
                fetchOrdersResponse(securityContext, ordersResponse.getMarker().toString());
            }
        }
    }

    public static Cache<String, List<OrdersResponse.Order>> getSellOrders() {
        if (SELL_ORDERS.size() == 0) {
            SCHEDULED_EXECUTOR.submit(DATA_FETCHER);
        }
        return SELL_ORDERS;
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
