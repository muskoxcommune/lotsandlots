package io.lotsandlots.etrade;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.lotsandlots.etrade.api.OrdersResponse;
import io.lotsandlots.etrade.oauth.SecurityContext;
import io.lotsandlots.util.DateFormatter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class EtradeOrdersDataFetcher extends EtradeDataFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(EtradeOrdersDataFetcher.class);

    private static EtradeOrdersDataFetcher DATA_FETCHER = null;

    private final Cache<Long, OrdersResponse.Order> orderCache = CacheBuilder
            .newBuilder()
            .expireAfterWrite(90, TimeUnit.SECONDS)
            .build();
    private Map<String, List<OrdersResponse.Order>> symbolToOrdersIndex = new HashMap<>();

    void fetchOrdersResponse(SecurityContext securityContext,
                                     String marker)
            throws GeneralSecurityException, UnsupportedEncodingException {
        Message ordersMessage = newOrdersMessage(marker);
        setOAuthHeader(securityContext, ordersMessage);
        ResponseEntity<OrdersResponse> ordersResponseResponseEntity = getRestTemplateFactory()
                .newCustomRestTemplate()
                .execute(ordersMessage, OrdersResponse.class);
        OrdersResponse ordersResponse = ordersResponseResponseEntity.getBody();
        if (ordersResponse == null) {
            throw new RuntimeException("Empty orders response");
        } else {
            handleOrderResponse(ordersResponse);
            if (ordersResponse.hasMarker()) {
                fetchOrdersResponse(securityContext, ordersResponse.getMarker().toString());
            }
        }
    }

    Cache<Long, OrdersResponse.Order> getOrderCache() {
        return orderCache;
    }

    public static Map<String, List<OrdersResponse.Order>> getSymbolToOrdersIndex() {
        return DATA_FETCHER.getSymbolToOrdersIndex(true);
    }

    Map<String, List<OrdersResponse.Order>> getSymbolToOrdersIndex(boolean runIfEmpty) {
        if (symbolToOrdersIndex.size() == 0 && runIfEmpty) {
            getScheduledExecutor().submit(this);
        }
        return symbolToOrdersIndex;
    }

    void handleOrderResponse(OrdersResponse ordersResponse) {
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
                        orderCache.put(order.getOrderId(), order);
                    }
                } else {
                    LOG.warn("Expected OrderDetail to include one Instrument");
                }
            } else {
                LOG.warn("Expected Order to include one OrderDetail");
            }
        }
    }

    void indexOrdersBySymbol() {
        Map<String, List<OrdersResponse.Order>> newSymbolToOrdersIndex = new HashMap<>();
        for (Map.Entry<Long, OrdersResponse.Order> entry : orderCache.asMap().entrySet()) {
            OrdersResponse.Order order = entry.getValue();
            if (newSymbolToOrdersIndex.containsKey(order.getSymbol())) {
                newSymbolToOrdersIndex.get(order.getSymbol()).add(order);
            } else {
                List<OrdersResponse.Order> newOrderList = new LinkedList<>();
                newOrderList.add(order);
                newSymbolToOrdersIndex.put(order.getSymbol(), newOrderList);
            }
        }
        symbolToOrdersIndex = newSymbolToOrdersIndex;
    }

    public static void init() {
        if (DATA_FETCHER == null) {
            DATA_FETCHER = new EtradeOrdersDataFetcher();
            DATA_FETCHER.getScheduledExecutor().scheduleAtFixedRate(DATA_FETCHER, 0, 60, TimeUnit.SECONDS);
        }
    }

    static Message newOrdersMessage(String marker) {
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
        return ordersMessage;
    }

    @Override
    public void run() {
        SecurityContext securityContext = EtradeRestTemplateFactory.getTemplateFactory().getSecurityContext();
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
            LOG.info("Fetched orders data, duration={}ms orders={}",
                    System.currentTimeMillis() - timeStartedMillis, getOrderCache().size());
        } catch (Exception e) {
            LOG.info("Failed to fetch orders data, duration={}ms",
                    System.currentTimeMillis() - timeStartedMillis, e);
        }
        indexOrdersBySymbol();
    }

    public static void shutdown() {
        DATA_FETCHER.getScheduledExecutor().shutdown();
    }
}
