package io.lotsandlots.etrade;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.lotsandlots.etrade.api.OrderDetail;
import io.lotsandlots.etrade.api.OrdersResponse;
import io.lotsandlots.etrade.oauth.SecurityContext;
import io.lotsandlots.etrade.rest.Message;
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
    private Map<String, List<OrdersResponse.Order>> symbolToBuyOrdersIndex = new HashMap<>();
    private Map<String, List<OrdersResponse.Order>> symbolToSellOrdersIndex = new HashMap<>();

    void fetchOrdersResponse(SecurityContext securityContext,
                             String marker)
            throws GeneralSecurityException, UnsupportedEncodingException {
        Message ordersMessage = newOrdersMessage(marker);
        setOAuthHeader(securityContext, ordersMessage);
        ResponseEntity<OrdersResponse> ordersResponseResponseEntity = getRestTemplateFactory()
                .newCustomRestTemplate()
                .doGet(ordersMessage, OrdersResponse.class);
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

    public static EtradeOrdersDataFetcher getDataFetcher() {
        return DATA_FETCHER;
    }

    Cache<Long, OrdersResponse.Order> getOrderCache() {
        return orderCache;
    }

    public static Map<String, List<OrdersResponse.Order>> getSymbolToBuyOrdersIndex() {
        return getSymbolToBuyOrdersIndex(true);
    }
    public static Map<String, List<OrdersResponse.Order>> getSymbolToBuyOrdersIndex(boolean runIfEmpty) {
        if (DATA_FETCHER.symbolToBuyOrdersIndex.size() == 0 && runIfEmpty) {
            DATA_FETCHER.getScheduledExecutor().submit(DATA_FETCHER);
        }
        return DATA_FETCHER.symbolToBuyOrdersIndex;
    }
    public static Map<String, List<OrdersResponse.Order>> getSymbolToBuyOrdersIndex(
            EtradeOrdersDataFetcher dataFetcher) {
        return dataFetcher.symbolToBuyOrdersIndex;
    }

    public static Map<String, List<OrdersResponse.Order>> getSymbolToSellOrdersIndex() {
        return getSymbolToSellOrdersIndex(true);
    }
    public static Map<String, List<OrdersResponse.Order>> getSymbolToSellOrdersIndex(boolean runIfEmpty) {
        if (DATA_FETCHER.symbolToSellOrdersIndex.size() == 0 && runIfEmpty) {
            DATA_FETCHER.getScheduledExecutor().submit(DATA_FETCHER);
        }
        return DATA_FETCHER.symbolToSellOrdersIndex;
    }

    void handleOrderResponse(OrdersResponse ordersResponse) {
        for (OrdersResponse.Order order : ordersResponse.getOrderList()) {
            List<OrderDetail> orderDetails = order.getOrderDetailList();
            if (orderDetails.size() == 1) {
                OrderDetail orderDetail = orderDetails.get(0);
                if (!orderDetail.getStatus().equals("OPEN") && !orderDetail.getStatus().equals("PARTIAL")) {
                    continue;
                }
                List<OrderDetail.Instrument> instruments = orderDetail.getInstrumentList();
                if (instruments.size() == 1) {
                    OrderDetail.Instrument instrument = instruments.get(0);
                    if (instrument.getOrderAction().equals("BUY") || instrument.getOrderAction().equals("SELL")) {
                        String symbol = instrument.getProduct().getSymbol();
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

    List<OrdersResponse.Order> newOrderList(OrdersResponse.Order order) {
        List<OrdersResponse.Order> newOrderList = new LinkedList<>();
        newOrderList.add(order);
        return newOrderList;
    }

    void indexOrdersBySymbol() {
        Map<String, List<OrdersResponse.Order>> newSymbolToBuyOrdersIndex = new HashMap<>();
        Map<String, List<OrdersResponse.Order>> newSymbolToSellOrdersIndex = new HashMap<>();
        for (Map.Entry<Long, OrdersResponse.Order> entry : orderCache.asMap().entrySet()) {
            OrdersResponse.Order order = entry.getValue();
            String action = order.getOrderDetailList().get(0).getInstrumentList().get(0).getOrderAction();
            if (action.equals("BUY")) {
                if (newSymbolToBuyOrdersIndex.containsKey(order.getSymbol())) {
                    newSymbolToBuyOrdersIndex.get(order.getSymbol()).add(order);
                } else {
                    newSymbolToBuyOrdersIndex.put(order.getSymbol(), newOrderList(order));
                }
            } else if (action.equals("SELL")) {
                if (newSymbolToSellOrdersIndex.containsKey(order.getSymbol())) {
                    newSymbolToSellOrdersIndex.get(order.getSymbol()).add(order);
                } else {
                    newSymbolToSellOrdersIndex.put(order.getSymbol(), newOrderList(order));
                }
            }
        }
        symbolToBuyOrdersIndex = newSymbolToBuyOrdersIndex;
        symbolToSellOrdersIndex = newSymbolToSellOrdersIndex;
    }

    public static void init() {
        if (DATA_FETCHER == null) {
            DATA_FETCHER = new EtradeOrdersDataFetcher();
            DATA_FETCHER
                    .getScheduledExecutor()
                    .scheduleAtFixedRate(DATA_FETCHER, 0, 60, TimeUnit.SECONDS);
        }
    }

    Message newOrdersMessage(String marker) {
        Message ordersMessage = new Message();
        ordersMessage.setRequiresOauth(true);
        ordersMessage.setHttpMethod("GET");
        ordersMessage.setUrl(getApiConfig().getOrdersUrl());
        String ordersQueryString = getApiConfig().getOrdersQueryString();

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

    public static void putOrderInCache(OrdersResponse.Order order) {
        if (DATA_FETCHER != null) {
            DATA_FETCHER.orderCache.put(order.getOrderId(), order);
        }
    }

    public static void refreshSymbolToOrdersIndexes() {
        if (DATA_FETCHER != null) {
            DATA_FETCHER.indexOrdersBySymbol();
        }
    }

    public static void removeOrderFromCache(Long orderId) {
        if (DATA_FETCHER != null) {
            DATA_FETCHER.orderCache.invalidate(orderId);
        }
    }

    @Override
    public void run() {
        SecurityContext securityContext = getRestTemplateFactory().getSecurityContext();
        if (!securityContext.isInitialized()) {
            LOG.warn("SecurityContext not initialized, please go to /etrade/authorize");
            return;
        }
        if (getApiConfig().getOrdersUrl() == null) {
            LOG.warn("Please configure etrade.accountIdKey");
            return;
        }
        LOG.info("Fetching orders data");
        long timeStartedMillis = System.currentTimeMillis();
        try {
            fetchOrdersResponse(securityContext, null);
            // Expired entries are not guaranteed to be cleaned up immediately.
            // Reference https://github.com/google/guava/wiki/CachesExplained#when-does-cleanup-happen
            orderCache.cleanUp();
            LOG.info("Fetched orders data, duration={}ms orders={}",
                    System.currentTimeMillis() - timeStartedMillis, orderCache.size());
            indexOrdersBySymbol();
        } catch (Exception e) {
            LOG.info("Failed to fetch orders data, duration={}ms",
                    System.currentTimeMillis() - timeStartedMillis, e);
        }
    }
}
