package io.lotsandlots.etrade;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.typesafe.config.Config;
import io.lotsandlots.etrade.api.OrderDetail;
import io.lotsandlots.etrade.api.OrdersResponse;
import io.lotsandlots.etrade.model.Order;
import io.lotsandlots.etrade.oauth.SecurityContext;
import io.lotsandlots.etrade.rest.Message;
import io.lotsandlots.util.ConfigWrapper;
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
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class EtradeOrdersDataFetcher extends EtradeDataFetcher {

    private static final Config CONFIG = ConfigWrapper.getConfig();
    private static final Logger LOG = LoggerFactory.getLogger(EtradeOrdersDataFetcher.class);

    private static EtradeOrdersDataFetcher DATA_FETCHER = null;

    private final Cache<Long, Order> orderCache;
    private boolean isStarted = false;
    private Long ordersDataExpirationSeconds = 120L;
    private Long ordersDataFetchIntervalSeconds = 60L;
    private Map<String, List<Order>> symbolToBuyOrdersIndex = new HashMap<>();
    private Map<String, List<Order>> symbolToSellOrdersIndex = new HashMap<>();


    EtradeOrdersDataFetcher() {
        setScheduledExecutor(Executors.newSingleThreadScheduledExecutor());

        if (CONFIG.hasPath("etrade.ordersDataExpirationSeconds")) {
            ordersDataExpirationSeconds = CONFIG.getLong("etrade.ordersDataExpirationSeconds");
        }
        if (CONFIG.hasPath("etrade.ordersDataFetchIntervalSeconds")) {
            ordersDataFetchIntervalSeconds = CONFIG.getLong("etrade.ordersDataFetchIntervalSeconds");
        }

        orderCache = CacheBuilder
                .newBuilder()
                .expireAfterWrite(ordersDataExpirationSeconds, TimeUnit.SECONDS)
                .build();
    }

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

    @VisibleForTesting
    Cache<Long, Order> getOrderCache() {
        return orderCache;
    }

    public Long getOrdersDataExpirationSeconds() {
        return ordersDataExpirationSeconds;
    }

    public Map<String, List<Order>> getSymbolToBuyOrdersIndex() {
        return getSymbolToBuyOrdersIndex(true);
    }
    public Map<String, List<Order>> getSymbolToBuyOrdersIndex(boolean runIfEmpty) {
        if (symbolToBuyOrdersIndex.size() == 0 && runIfEmpty) {
            getScheduledExecutor().submit(this);
        }
        return symbolToBuyOrdersIndex;
    }
    @VisibleForTesting
    public static Map<String, List<Order>> getSymbolToBuyOrdersIndex(
            EtradeOrdersDataFetcher dataFetcher) {
        return dataFetcher.symbolToBuyOrdersIndex;
    }

    public Map<String, List<Order>> getSymbolToSellOrdersIndex() {
        return getSymbolToSellOrdersIndex(true);
    }
    public Map<String, List<Order>> getSymbolToSellOrdersIndex(boolean runIfEmpty) {
        if (symbolToSellOrdersIndex.size() == 0 && runIfEmpty) {
            getScheduledExecutor().submit(this);
        }
        return symbolToSellOrdersIndex;
    }
    @VisibleForTesting
    public void setSymbolToSellOrdersIndex(Map<String, List<Order>> symbolToSellOrdersIndex) {
        this.symbolToSellOrdersIndex = symbolToSellOrdersIndex;
    }

    void handleOrderResponse(OrdersResponse ordersResponse) {
        for (OrdersResponse.Order ordersResponseOrder : ordersResponse.getOrderList()) {
            List<OrderDetail> orderDetails = ordersResponseOrder.getOrderDetailList();
            if (orderDetails.size() == 1) {
                OrderDetail orderDetail = orderDetails.get(0);
                if (!orderDetail.getStatus().equals("OPEN") && !orderDetail.getStatus().equals("PARTIAL")) {
                    continue;
                }
                List<OrderDetail.Instrument> instruments = orderDetail.getInstrumentList();
                if (instruments.size() == 1) {
                    OrderDetail.Instrument instrument = instruments.get(0);
                    if (instrument.getOrderAction().equals("BUY") || instrument.getOrderAction().equals("SELL")) {
                        Order order = new Order();
                        order.setOrderId(ordersResponseOrder.getOrderId());
                        order.setLimitPrice(orderDetail.getLimitPrice());
                        order.setOrderAction(instrument.getOrderAction());
                        order.setOrderedQuantity(instrument.getOrderedQuantity());
                        order.setPlacedTime(orderDetail.getPlacedTime());
                        order.setStatus(orderDetail.getStatus());
                        order.setSymbol(instrument.getProduct().getSymbol());
                        orderCache.put(ordersResponseOrder.getOrderId(), order);
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
        Map<String, List<Order>> newSymbolToBuyOrdersIndex = new HashMap<>();
        Map<String, List<Order>> newSymbolToSellOrdersIndex = new HashMap<>();
        for (Map.Entry<Long, Order> entry : orderCache.asMap().entrySet()) {
            Order order = entry.getValue();
            String action = order.getOrderAction();
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

    List<Order> newOrderList(Order order) {
        List<Order> newOrderList = new LinkedList<>();
        newOrderList.add(order);
        return newOrderList;
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

    public static void putOrderInCache(Order order) {
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
            // Expired entries are not guaranteed to be cleaned up immediately, so we do it here explicitly.
            // Reference https://github.com/google/guava/wiki/CachesExplained#when-does-cleanup-happen
            orderCache.cleanUp();
            indexOrdersBySymbol();
            long currentTimeMillis = System.currentTimeMillis();
            LOG.info("Fetched orders data, duration={}ms orders={}",
                    currentTimeMillis - timeStartedMillis, orderCache.size());
            setLastSuccessfulFetchTimeMillis(currentTimeMillis);
        } catch (Exception e) {
            long currentTimeMillis = System.currentTimeMillis();
            LOG.info("Failed to fetch orders data, duration={}ms", currentTimeMillis - timeStartedMillis, e);
            setLastFailedFetchTimeMillis(currentTimeMillis);
        }
    }

    public static void start() {
        if (DATA_FETCHER == null) {
            init();
        }
        if (!DATA_FETCHER.isStarted) {
            DATA_FETCHER
                    .getScheduledExecutor()
                    .scheduleAtFixedRate(
                            DATA_FETCHER, 0, DATA_FETCHER.ordersDataFetchIntervalSeconds, TimeUnit.SECONDS);
            DATA_FETCHER.isStarted = true;
        }
    }
}
