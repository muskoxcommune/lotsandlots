package io.lotsandlots.etrade;

import com.typesafe.config.Config;
import io.lotsandlots.data.SqliteDatabase;
import io.lotsandlots.etrade.api.PortfolioResponse;
import io.lotsandlots.etrade.api.PositionLotsResponse;
import io.lotsandlots.etrade.oauth.SecurityContext;
import io.lotsandlots.etrade.rest.Message;
import io.lotsandlots.util.ConfigWrapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

public class EtradePortfolioDataFetcher extends EtradeDataFetcher {

    private static final Config CONFIG = ConfigWrapper.getConfig();
    private static final SqliteDatabase DB = SqliteDatabase.getInstance();
    private static final Logger LOG = LoggerFactory.getLogger(EtradePortfolioDataFetcher.class);

    private final List<OnPortfolioDataFetchCompletionHandler> onPortfolioDataFetchCompletionHandlers = new LinkedList<>();
    private final List<OnPositionLotsUpdateHandler> onPositionLotsUpdateHandlers = new LinkedList<>();
    private Long portfolioDataExpirationSeconds = 120L;
    private Long portfolioDataFetchIntervalSeconds = 60L;
    private Double defaultOrderCreationThreshold = 0.03;
    private PortfolioResponse.Totals totals = new PortfolioResponse.Totals();

    public EtradePortfolioDataFetcher() {
        if (CONFIG.hasPath("etrade.defaultOrderCreationThreshold")) {
            defaultOrderCreationThreshold = CONFIG.getDouble("etrade.defaultOrderCreationThreshold");
        }
        if (CONFIG.hasPath("etrade.portfolioDataExpirationSeconds")) {
            portfolioDataExpirationSeconds = CONFIG.getLong("etrade.portfolioDataExpirationSeconds");
        }
        if (CONFIG.hasPath("etrade.portfolioDataFetchInterval")) {
            portfolioDataFetchIntervalSeconds = CONFIG.getLong("etrade.portfolioDataFetchInterval");
        }
        try {
            DB.executeSql(
                    "CREATE TABLE IF NOT EXISTS etrade_lot ("
                            + "acquired_date integer,"
                            + "acquired_price real,"
                            + "follow_price real,"
                            + "last_price real,"
                            + "lot_id text PRIMARY KEY,"
                            + "remaining_qty real,"
                            + "symbol text,"
                            + "target_price real,"
                            + "updated_time integer"
                            + ");"
            );
        } catch (SQLException e) {
            LOG.error("Failed to create 'etrade_lot' table", e);
        }
        LOG.info("Initialized EtradePortfolioDataFetcher, defaultOrderCreationThreshold={} "
                        + "portfolioDataExpirationSeconds={} portfolioDataFetchIntervalSeconds={}",
                defaultOrderCreationThreshold, portfolioDataExpirationSeconds, portfolioDataFetchIntervalSeconds);
    }

    public void addOnPortfolioDataFetchCompletionHandler(OnPortfolioDataFetchCompletionHandler handler) {
        onPortfolioDataFetchCompletionHandlers.add(handler);
    }

    public void addOnPositionLotsUpdateHandler(OnPositionLotsUpdateHandler handler) {
        onPositionLotsUpdateHandlers.add(handler);
    }

    /**
     * Call E*Trade's portfolio API and process position data.
     *
     * @param securityContext SecurityContext object from EtradeRestTemplateFactory.
     * @param pageNumber String for paginating results. Null for initial invocation.
     * @throws GeneralSecurityException
     * @throws UnsupportedEncodingException
     */
    void fetchPortfolioResponse(SecurityContext securityContext,
                                String pageNumber)
            throws GeneralSecurityException, UnsupportedEncodingException {
        Message portfolioMessage = new Message();
        portfolioMessage.setRequiresOauth(true);
        portfolioMessage.setHttpMethod("GET");
        portfolioMessage.setUrl(getApiConfig().getPortfolioUrl());
        String portfolioQueryString = getApiConfig().getPortfolioQueryString();
        if (!StringUtils.isBlank(pageNumber)) {
            portfolioQueryString += "&pageNumber=" + pageNumber;
        }
        portfolioMessage.setQueryString(portfolioQueryString);
        setOAuthHeader(securityContext, portfolioMessage);
        ResponseEntity<PortfolioResponse> portfolioResponseResponseEntity = getRestTemplateFactory()
                .newCustomRestTemplate()
                .doGet(portfolioMessage, PortfolioResponse.class);
        PortfolioResponse portfolioResponse = portfolioResponseResponseEntity.getBody();
        if (portfolioResponse == null) {
            throw new RuntimeException("Empty portfolio response");
        } else {
            totals = portfolioResponse.getTotals(); // Update portfolio totals
            LOG.info("Portfolio cash={}, todaysGainLossPct={}, todaysGainLoss={}, "
                            + "totalGainLossPct={}, totalGainLoss={}, totalPaid={}, totalValue={}",
                    totals.getCashBalance(),
                    totals.getTodaysGainLossPct(),
                    totals.getTodaysGainLoss(),
                    totals.getTotalGainLossPct(),
                    totals.getTotalGainLoss(),
                    totals.getTotalPricePaid(),
                    totals.getTotalMarketValue()
            );
            PortfolioResponse.AccountPortfolio accountPortfolio = portfolioResponse.getAccountPortfolio();
            for (PortfolioResponse.Position freshPositionData : accountPortfolio.getPositionList()) {
                String symbol = freshPositionData.getSymbolDescription();
                fetchPositionLotsResponse(securityContext, symbol, freshPositionData);
            }
            if (accountPortfolio.hasNextPageNo()) {
                fetchPortfolioResponse(securityContext, accountPortfolio.getNextPageNo());
            }
        }
    }

    void fetchPositionLotsResponse(SecurityContext securityContext,
                                   String symbol,
                                   PortfolioResponse.Position position)
            throws GeneralSecurityException, UnsupportedEncodingException{
        Message lotsMessage = new Message();
        lotsMessage.setRequiresOauth(true);
        lotsMessage.setHttpMethod("GET");
        lotsMessage.setUrl(position.getLotsDetails());
        setOAuthHeader(securityContext, lotsMessage);
        ResponseEntity<PositionLotsResponse> positionLotsResponseResponseEntity = getRestTemplateFactory()
                .newCustomRestTemplate()
                .doGet(lotsMessage, PositionLotsResponse.class);
        PositionLotsResponse positionLotsResponse = positionLotsResponseResponseEntity.getBody();
        if (positionLotsResponse == null) {
            throw new RuntimeException("Empty response");
        } else {
            Double orderCreationThreshold = defaultOrderCreationThreshold;
            String overrideOrderCreationThresholdPath = "etrade.overrideOrderCreationThresholds." + symbol;
            if (CONFIG.hasPath(overrideOrderCreationThresholdPath)) {
                orderCreationThreshold = CONFIG.getDouble(overrideOrderCreationThresholdPath);
            }
            Integer lotCount = positionLotsResponse.getPositionLots().size();
            for (PositionLotsResponse.PositionLot lot : positionLotsResponse.getPositionLots()) {
                lot.setSymbol(symbol);
                lot.setTotalLotCount(lotCount);
                lot.setTotalPositionCost(position.getTotalCost());
                lot.setPositionPctOfPortfolio(position.getPctOfPortfolio());
                lot.setOrderCreationThreshold(orderCreationThreshold);
                lot.setFollowPrice(lot.getPrice() * (1F - orderCreationThreshold.floatValue()));
                lot.setTargetPrice(lot.getPrice() * (1F + orderCreationThreshold.floatValue()));

                LotInsertPreparedStatementCallback callback = new LotInsertPreparedStatementCallback(
                        lot, orderCreationThreshold.floatValue());
                try {
                    DB.executePreparedUpdate(
                            "INSERT OR REPLACE INTO etrade_lot ("
                                    + "acquired_date,"
                                    + "acquired_price,"
                                    + "follow_price,"
                                    + "last_price,"
                                    + "lot_id,"
                                    + "remaining_qty,"
                                    + "symbol,"
                                    + "target_price,"
                                    + "updated_time"
                                + ") VALUES(?,?,?,?,?,?,?,?,?);",
                            callback);
                } catch (SQLException e) {
                    LOG.error("Failed to execute: {}", callback.getStatement(), e);
                }
            }
            for (OnPositionLotsUpdateHandler handler : onPositionLotsUpdateHandlers) {
                handler.handlePositionLotsUpdate(symbol, totals);
            }
        }
    }

    public Long getPortfolioDataExpirationSeconds() {
        return portfolioDataExpirationSeconds;
    }

    public Long getPortfolioDataFetchIntervalSeconds() {
        return portfolioDataFetchIntervalSeconds;
    }

    @Override
    public void run() {
        SecurityContext securityContext = getRestTemplateFactory().getSecurityContext();
        if (!securityContext.isInitialized()) {
            LOG.warn("SecurityContext not initialized, please go to /etrade/authorize");
            return;
        }
        if (getApiConfig().getPortfolioUrl() == null) {
            LOG.warn("Please configure etrade.accountIdKey");
            return;
        }
        LOG.info("Fetching portfolio data");
        long timeStartedMillis = System.currentTimeMillis();
        try {
            fetchPortfolioResponse(securityContext, null);
            long timeStoppedMillis = System.currentTimeMillis();
            setLastSuccessfulFetchTimeMillis(timeStoppedMillis);
            LOG.info("Fetched portfolio and lots data, duration={}ms", timeStoppedMillis - timeStartedMillis);
            for (OnPortfolioDataFetchCompletionHandler handler : onPortfolioDataFetchCompletionHandlers) {
                try {
                    handler.handlePortfolioDataFetchCompletion(timeStartedMillis, timeStoppedMillis, totals);
                } catch (Exception e) {
                    LOG.error("Failed to handle portfolio data fetch completion event");
                }
            }
        } catch (Exception e) {
            long timeFailedMillis = System.currentTimeMillis();
            LOG.info("Failed to fetch portfolio and lots data, duration={}ms", timeFailedMillis - timeStartedMillis, e);
            setLastFailedFetchTimeMillis(timeFailedMillis);
        }
    }

    static class LotInsertPreparedStatementCallback implements SqliteDatabase.PreparedStatementCallback {

        private final PositionLotsResponse.PositionLot lot;
        private final float orderCreationThreshold;
        private PreparedStatement statement;

        LotInsertPreparedStatementCallback(PositionLotsResponse.PositionLot lot, float orderCreationThreshold) {
            this.lot = lot;
            this.orderCreationThreshold = orderCreationThreshold;
        }

        @Override
        public void call(PreparedStatement stmt) throws SQLException {
            this.statement = stmt;
            stmt.setInt(1, (int) (lot.getAcquiredDate() / 1000L));
            stmt.setFloat(2, lot.getPrice());
            stmt.setFloat(3, lot.getPrice() * (1F - orderCreationThreshold));
            stmt.setFloat(4, lot.getMarketValue() / lot.getRemainingQty());
            stmt.setString(5, lot.getPositionLotId().toString());
            stmt.setFloat(6, lot.getRemainingQty());
            stmt.setString(7, lot.getSymbol());
            stmt.setFloat(8, lot.getPrice() * (1F + orderCreationThreshold));
            stmt.setInt(9, (int) (System.currentTimeMillis() / 1000L));
            stmt.executeUpdate();
        }

        public PreparedStatement getStatement() {
            return statement;
        }
    }

    public interface OnPositionLotsUpdateHandler {

        void handlePositionLotsUpdate(String symbol, PortfolioResponse.Totals totals);
    }

    public interface OnPortfolioDataFetchCompletionHandler {

        void handlePortfolioDataFetchCompletion(
                long timeFetchStarted, long timeFetchStopped, PortfolioResponse.Totals totals);
    }
}
