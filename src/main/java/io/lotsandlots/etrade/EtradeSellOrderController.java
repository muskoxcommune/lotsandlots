package io.lotsandlots.etrade;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.typesafe.config.Config;
import io.lotsandlots.data.SqliteDatabase;
import io.lotsandlots.etrade.api.CancelOrderRequest;
import io.lotsandlots.etrade.api.CancelOrderResponse;
import io.lotsandlots.etrade.api.OrderDetail;
import io.lotsandlots.etrade.api.PortfolioResponse;
import io.lotsandlots.etrade.oauth.SecurityContext;
import io.lotsandlots.etrade.rest.Message;
import io.lotsandlots.util.ConfigWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.GeneralSecurityException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EtradeSellOrderController implements EtradePortfolioDataFetcher.OnPositionLotsUpdateHandler {

    private static final Config CONFIG = ConfigWrapper.getConfig();
    private static final SqliteDatabase DB = SqliteDatabase.getInstance();
    private static final ExecutorService DEFAULT_EXECUTOR = Executors.newFixedThreadPool(5);
    private static final Logger LOG = LoggerFactory.getLogger(EtradeSellOrderController.class);

    private boolean cancelAllOrdersOnLotsOrdersMismatch = true;
    private final ExecutorService executor;
    private final EtradeOrdersDataFetcher ordersDataFetcher;
    private final EtradePortfolioDataFetcher portfolioDataFetcher;
    private final List<String> sellOrderDisabledSymbols = new LinkedList<>();

    public EtradeSellOrderController(EtradePortfolioDataFetcher portfolioDataFetcher,
                                     EtradeOrdersDataFetcher ordersDataFetcher) {
        this(portfolioDataFetcher, ordersDataFetcher, DEFAULT_EXECUTOR);
    }

    public EtradeSellOrderController(EtradePortfolioDataFetcher portfolioDataFetcher,
                                     EtradeOrdersDataFetcher ordersDataFetcher,
                                     ExecutorService executor) {
        if (CONFIG.hasPath("etrade.cancelAllOrdersOnLotsOrdersMismatch")) {
            cancelAllOrdersOnLotsOrdersMismatch = CONFIG.getBoolean("etrade.cancelAllOrdersOnLotsOrdersMismatch");
        }
        if (CONFIG.hasPath("etrade.disableSellOrderCreation")) {
            sellOrderDisabledSymbols.addAll(CONFIG.getStringList("etrade.disableSellOrderCreation"));
        }

        this.executor = executor;
        this.ordersDataFetcher = ordersDataFetcher;
        this.portfolioDataFetcher = portfolioDataFetcher;

        portfolioDataFetcher.addOnPositionLotsUpdateHandler(this);

        LOG.info("Initialized EtradeSellOrderCreator, cancelAllOrdersOnLotsOrdersMismatch={} sellOrderDisabledSymbols={}",
                cancelAllOrdersOnLotsOrdersMismatch, sellOrderDisabledSymbols
        );
    }

    @Override
    public void handlePositionLotsUpdate(String symbol,
                                         PortfolioResponse.Totals totals) {
        if (isSellOrderCreationDisabled(symbol)) {
            LOG.debug("Skipping disabled sell order creation feature, symbol={}", symbol);
        } else {
            Long lastSuccessfulFetchTimeMillis = ordersDataFetcher.getLastSuccessfulFetchTimeMillis();
            if (lastSuccessfulFetchTimeMillis == null) {
                LOG.debug("Skipping sell order creation, orders data fetch has not occurred, symbol={}", symbol);
                return;
            }
            long currentTimeMillis = System.currentTimeMillis();
            long deltaMillis = currentTimeMillis - lastSuccessfulFetchTimeMillis;
            long thresholdMillis = ordersDataFetcher.getOrdersDataExpirationSeconds() * 1000L;
            if (deltaMillis > thresholdMillis) {
                LOG.warn("Skipping sell order creation due to orders data staleness, "
                                + "lastSuccessfulFetchTimeMillis={} deltaMillis={} thresholdMillis={} symbol={}",
                        lastSuccessfulFetchTimeMillis, deltaMillis, thresholdMillis, symbol);
            } else {
                executor.submit(new SymbolToLotsIndexPutEventRunnable(symbol));
            }
        }
    }

    boolean isSellOrderCreationDisabled(String symbol) {
        return sellOrderDisabledSymbols.contains(symbol);
    }

    SymbolToLotsIndexPutEventRunnable newSymbolToLotsIndexPutEventRunnable(String symbol) {
        return new SymbolToLotsIndexPutEventRunnable(symbol);
    }

    class SymbolToLotsIndexPutEventRunnable extends EtradeOrderCreator {

        private final String symbol;

        SymbolToLotsIndexPutEventRunnable(String symbol) {
            this.symbol = symbol;
        }

        void cancelOrder(SecurityContext securityContext, Long orderId)
                throws GeneralSecurityException, JsonProcessingException, UnsupportedEncodingException {
            CancelOrderRequest cancelOrderRequest = new CancelOrderRequest();
            cancelOrderRequest.setOrderId(orderId);
            if (LOG.isDebugEnabled()) {
                LOG.debug("CancelOrderRequest{}", OBJECT_MAPPER.writeValueAsString(cancelOrderRequest));
            }
            Map<String, CancelOrderRequest> payload = new HashMap<>();
            payload.put("CancelOrderRequest", cancelOrderRequest);

            Message orderCancelMessage = new Message();
            orderCancelMessage.setRequiresOauth(true);
            orderCancelMessage.setHttpMethod("PUT");
            orderCancelMessage.setContentType(MediaType.APPLICATION_JSON_VALUE);
            orderCancelMessage.setUrl(getApiConfig().getOrdersCancelUrl());
            setOAuthHeader(securityContext, orderCancelMessage);
            ResponseEntity<CancelOrderResponse> cancelOrderResponseEntity =
                    getRestTemplateFactory()
                            .newCustomRestTemplate()
                            .doPut(orderCancelMessage,
                                   OBJECT_MAPPER.writeValueAsString(payload),
                                   CancelOrderResponse.class);
            CancelOrderResponse cancelOrderResponse = cancelOrderResponseEntity.getBody();
            if (cancelOrderResponse == null) {
                throw new RuntimeException("Empty cancel order response");
            } else if (LOG.isDebugEnabled()) {
                LOG.debug("CancelOrderResponse{}", OBJECT_MAPPER.writeValueAsString(cancelOrderResponse));
            }
            String sql = "DELETE FROM etrade_order WHERE order_id == ?;";
            try (Connection conn = DB.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, orderId.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                LOG.error("Failed to select from 'etrade_order', symbol={}", symbol, e);
            }
        }

        @Override
        public void run() {
            SecurityContext securityContext = getRestTemplateFactory().getSecurityContext();
            if (!securityContext.isInitialized()) {
                LOG.warn("SecurityContext not initialized, please go to /etrade/authorize");
                return;
            }
            if (getApiConfig().getOrdersPreviewUrl() == null) {
                LOG.warn("Please configure etrade.accountIdKey");
                return;
            }
            int freshnessThreshold = (int) (
                    (System.currentTimeMillis() / 1000L) - portfolioDataFetcher.getPortfolioDataExpirationSeconds()
            );
            String sql;

            List<String> sellOrderIdList = new LinkedList<>();
            sql = "SELECT order_id FROM etrade_order WHERE symbol == ? AND order_action == \"SELL\" AND updated_time > ?;";
            try (Connection conn = DB.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, symbol);
                stmt.setInt(2, freshnessThreshold);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    sellOrderIdList.add(rs.getString("order_id"));
                }
            } catch (SQLException e) {
                LOG.error("Failed to select from 'etrade_order', symbol={}", symbol, e);
                return;
            }
            // TODO: Check for orders fetch completion.
            int lotCount;
            sql = "SELECT COUNT(*) FROM etrade_lot WHERE symbol == ? AND updated_time > ?;";
            try (Connection conn = DB.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, symbol);
                stmt.setInt(2, freshnessThreshold);
                lotCount = stmt.executeQuery().getInt(1);
            } catch (SQLException e) {
                LOG.error("Failed to select from 'etrade_lot', symbol={}", symbol, e);
                return;
            }
            LOG.debug("Found {} sell orders for {} lots, symbol={}", sellOrderIdList.size(), lotCount, symbol);
            if (sellOrderIdList.size() == lotCount) {
                return;
            }
            if (!cancelAllOrdersOnLotsOrdersMismatch) {
                LOG.warn("Skipping sell order creation, unable to proceed with mismatch, "
                        + "cancelAllOrdersOnLotsOrdersMismatch=false");
                return;
            }
            if (sellOrderIdList.size() > 0) {
                LOG.info("Canceling {} existing sell orders, symbol={}", sellOrderIdList.size(), symbol);
                try {
                    for (String orderId : sellOrderIdList) {
                        cancelOrder(securityContext, Long.parseLong(orderId));
                    }
                } catch (Exception e) {
                    LOG.debug("Failed to cancel all sell orders, symbol={}", symbol, e);
                    return;
                }
            }
            //
            LOG.info("Creating sell orders for {} lots, symbol={}", lotCount, symbol);
            sql = "SELECT lot_id,remaining_qty,target_price FROM etrade_lot WHERE symbol == ? AND updated_time > ?;";
            try (Connection conn = DB.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, symbol);
                stmt.setInt(2, freshnessThreshold);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    Float remainingQty = rs.getFloat("remaining_qty");

                    String clientOrderId = UUID.randomUUID().toString().substring(0, 8);

                    OrderDetail.Product product = new OrderDetail.Product();
                    product.setSecurityType("EQ");
                    product.setSymbol(symbol);

                    OrderDetail.Lots instrumentLots = new OrderDetail.Lots();
                    instrumentLots.newLotList(
                            Long.parseLong(rs.getString("lot_id")),
                            remainingQty.longValue());

                    OrderDetail.Instrument instrument = new OrderDetail.Instrument();
                    instrument.setLots(instrumentLots);
                    instrument.setOrderAction("SELL");
                    instrument.setProduct(product);
                    instrument.setQuantity(remainingQty.longValue());
                    instrument.setQuantityType("QUANTITY");

                    OrderDetail orderDetail = new OrderDetail();
                    orderDetail.setAllOrNone(false);
                    orderDetail.newInstrumentList(instrument);
                    orderDetail.setOrderTerm("GOOD_UNTIL_CANCEL");
                    orderDetail.setMarketSession("REGULAR");
                    orderDetail.setPriceType("LIMIT");
                    orderDetail.setLimitPrice(
                            BigDecimal.valueOf(rs.getFloat("target_price"))
                                    .setScale(2, RoundingMode.HALF_UP)
                                    .floatValue());
                    placeOrder(securityContext, clientOrderId, orderDetail);
                }
            } catch (Exception e) {
                LOG.debug("Unable to finish creating sell orders, symbol={}", symbol, e);
            }
        }
    }
}