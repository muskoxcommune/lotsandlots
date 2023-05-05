package io.lotsandlots.etrade;

import com.typesafe.config.Config;
import io.lotsandlots.data.SqliteDatabase;
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
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EtradeOrdersDataFetcher extends EtradeDataFetcher {

    private static final Config CONFIG = ConfigWrapper.getConfig();
    private static final SqliteDatabase DB = SqliteDatabase.getInstance();
    private static final Logger LOG = LoggerFactory.getLogger(EtradeOrdersDataFetcher.class);

    private Long ordersDataExpirationSeconds = 120L;
    private Long ordersDataFetchIntervalSeconds = 60L;
    private Map<String, List<Order>> symbolToBuyOrdersIndex = new HashMap<>();
    private Map<String, List<Order>> symbolToSellOrdersIndex = new HashMap<>();


    public EtradeOrdersDataFetcher() {
        if (CONFIG.hasPath("etrade.ordersDataExpirationSeconds")) {
            ordersDataExpirationSeconds = CONFIG.getLong("etrade.ordersDataExpirationSeconds");
        }
        if (CONFIG.hasPath("etrade.ordersDataFetchIntervalSeconds")) {
            ordersDataFetchIntervalSeconds = CONFIG.getLong("etrade.ordersDataFetchIntervalSeconds");
        }
        try {
            DB.executeSql(
                    "CREATE TABLE IF NOT EXISTS etrade_order ("
                            + "limit_price real,"
                            + "order_action text,"
                            + "order_id text PRIMARY KEY,"
                            + "ordered_quantity integer,"
                            + "placed_time integer,"
                            + "status text,"
                            + "symbol text,"
                            + "updated_time integer"
                            + ");"
            );
        } catch (SQLException e) {
            LOG.error("Failed to create 'order' table", e);
        }
        LOG.info("Initialized EtradeOrdersDataFetcher, ordersDataExpirationSeconds={} ordersDataFetchIntervalSeconds={}",
                ordersDataExpirationSeconds, ordersDataFetchIntervalSeconds);
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

    public Long getOrdersDataExpirationSeconds() {
        return ordersDataExpirationSeconds;
    }

    public Long getOrdersDataFetchIntervalSeconds() {
        return ordersDataFetchIntervalSeconds;
    }

    public Map<String, List<Order>> getSymbolToBuyOrdersIndex() {
        return symbolToBuyOrdersIndex;
    }

    public Map<String, List<Order>> getSymbolToSellOrdersIndex() {
        return symbolToSellOrdersIndex;
    }
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
                OrderInsertPreparedStatementCallback callback = new OrderInsertPreparedStatementCallback(
                        ordersResponseOrder.getOrderId().toString(), orderDetail);
                try {
                    DB.executePreparedUpdate(
                            "INSERT OR REPLACE INTO etrade_order ("
                                    + "limit_price,"
                                    + "order_action,"
                                    + "order_id,"
                                    + "ordered_quantity,"
                                    + "placed_time,"
                                    + "status,"
                                    + "symbol,"
                                    + "updated_time"
                                + ") VALUES(?,?,?,?,?,?,?,?);",
                            callback);
                } catch (SQLException e) {
                    LOG.error("Failed to execute: {}", callback.getStatement(), e);
                }
            } else {
                LOG.warn("Expected Order to include one OrderDetail");
            }
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
            long currentTimeMillis = System.currentTimeMillis();
            LOG.info("Fetched orders data, duration={}ms", currentTimeMillis - timeStartedMillis);
            setLastSuccessfulFetchTimeMillis(currentTimeMillis);
        } catch (Exception e) {
            long currentTimeMillis = System.currentTimeMillis();
            LOG.info("Failed to fetch orders data, duration={}ms", currentTimeMillis - timeStartedMillis, e);
            setLastFailedFetchTimeMillis(currentTimeMillis);
        }
    }

    static class OrderInsertPreparedStatementCallback implements SqliteDatabase.PreparedStatementCallback {

        private final OrderDetail orderDetail;
        private final String orderId;

        private PreparedStatement statement;

        OrderInsertPreparedStatementCallback(String orderId, OrderDetail orderDetail) {
            this.orderDetail = orderDetail;
            this.orderId = orderId;
        }

        @Override
        public void call(PreparedStatement stmt) throws SQLException {
            this.statement = stmt;
            List<OrderDetail.Instrument> instruments = orderDetail.getInstrumentList();
            if (instruments.size() == 1) {
                OrderDetail.Instrument instrument = instruments.get(0);
                if (instrument.getOrderAction().equals("BUY") || instrument.getOrderAction().equals("SELL")) {
                    stmt.setFloat(1, orderDetail.getLimitPrice());
                    stmt.setString(2, instrument.getOrderAction());
                    stmt.setString(3, orderId);
                    stmt.setInt(4, instrument.getOrderedQuantity().intValue());
                    stmt.setInt(5, (int) (orderDetail.getPlacedTime() / 1000L));
                    stmt.setString(6, orderDetail.getStatus());
                    stmt.setString(7, instrument.getProduct().getSymbol());
                    stmt.setInt(8, (int) (System.currentTimeMillis() / 1000L));
                    stmt.executeUpdate();
                }
            } else {
                LOG.warn("Expected OrderDetail to include one Instrument");
            }
        }

        public PreparedStatement getStatement() {
            return statement;
        }
    }
}
