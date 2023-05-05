package io.lotsandlots.etrade;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.typesafe.config.Config;
import io.lotsandlots.data.SqliteDatabase;
import io.lotsandlots.etrade.api.OrderDetail;
import io.lotsandlots.etrade.api.PortfolioResponse;
import io.lotsandlots.etrade.api.QuoteResponse;
import io.lotsandlots.etrade.model.Order;
import io.lotsandlots.etrade.oauth.SecurityContext;
import io.lotsandlots.etrade.rest.Message;
import io.lotsandlots.util.ConfigWrapper;
import io.lotsandlots.util.EmailHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class EtradeBuyOrderController implements EtradePortfolioDataFetcher.OnPortfolioDataFetchCompletionHandler,
        EtradePortfolioDataFetcher.OnPositionLotsUpdateHandler {

    private static final Config CONFIG = ConfigWrapper.getConfig();
    private static final SqliteDatabase DB = SqliteDatabase.getInstance();
    private static final ExecutorService DEFAULT_EXECUTOR = Executors.newFixedThreadPool(5);
    private static final Logger LOG = LoggerFactory.getLogger(EtradeBuyOrderController.class);

    private final ExecutorService executor;
    private final Set<String> buyOrderEnabledSymbols = new HashSet<>();
    private final Map<String, Cache<Long, Order>> placedBuyOrderCache = new HashMap<>();

    private EmailHelper emailHelper = new EmailHelper();
    private EtradePortfolioDataFetcher portfolioDataFetcher;
    private EtradeOrdersDataFetcher ordersDataFetcher;
    private int buyOrderCreationStartDayOfWeek = 1;
    private int buyOrderCreationStartHour = 13;
    private int buyOrderCreationStartMinute = 30;
    private int buyOrderCreationStopDayOfWeek = 6;
    private int buyOrderCreationStopHour = 20;
    private long haltBuyOrderCashBalance = 0L;
    private float idealLotSize = 1000F;
    private long maxBuyOrdersPerSymbolPerDay = 3L;
    private float minLotSize = 900F;

    public EtradeBuyOrderController(EtradePortfolioDataFetcher portfolioDataFetcher,
                                    EtradeOrdersDataFetcher ordersDataFetcher) {
        this(portfolioDataFetcher, ordersDataFetcher, DEFAULT_EXECUTOR);
    }

    public EtradeBuyOrderController(EtradePortfolioDataFetcher portfolioDataFetcher,
                                    EtradeOrdersDataFetcher ordersDataFetcher,
                                    ExecutorService executor) {
        this.executor = executor;
        this.ordersDataFetcher = ordersDataFetcher;
        this.portfolioDataFetcher = portfolioDataFetcher;

        if (CONFIG.hasPath("etrade.enableBuyOrderCreation")) {
            buyOrderEnabledSymbols.addAll(CONFIG.getStringList("etrade.enableBuyOrderCreation"));
        }
        if (CONFIG.hasPath("etrade.buyOrderCreationStartDayOfWeek")) {
            buyOrderCreationStartDayOfWeek = CONFIG.getInt("etrade.buyOrderCreationStartDayOfWeek");
        }
        if (CONFIG.hasPath("etrade.buyOrderCreationStartHour")) {
            buyOrderCreationStartHour = CONFIG.getInt("etrade.buyOrderCreationStartHour");
        }
        if (CONFIG.hasPath("etrade.buyOrderCreationStartMinute")) {
            buyOrderCreationStartMinute = CONFIG.getInt("etrade.buyOrderCreationStartMinute");
        }
        if (CONFIG.hasPath("etrade.buyOrderCreationStopDayOfWeek")) {
            buyOrderCreationStopDayOfWeek = CONFIG.getInt("etrade.buyOrderCreationStopDayOfWeek");
        }
        if (CONFIG.hasPath("etrade.buyOrderCreationStopHour")) {
            buyOrderCreationStopHour = CONFIG.getInt("etrade.buyOrderCreationStopHour");
        }
        if (CONFIG.hasPath("etrade.haltBuyOrderCashBalance")) {
            haltBuyOrderCashBalance = CONFIG.getLong("etrade.haltBuyOrderCashBalance");
        }
        if (CONFIG.hasPath("etrade.idealLotSize")) {
            idealLotSize = (float) CONFIG.getLong("etrade.idealLotSize");
        }
        if (CONFIG.hasPath("etrade.maxBuyOrdersPerSymbolPerDay")) {
            maxBuyOrdersPerSymbolPerDay = CONFIG.getLong("etrade.maxBuyOrdersPerSymbolPerDay");
        }
        if (CONFIG.hasPath("etrade.minLotSize")) {
            minLotSize = (float) CONFIG.getLong("etrade.minLotSize");
        }
        try {
            DB.executeSql(
                    "CREATE TABLE IF NOT EXISTS placed_etrade_buy_order ("
                            + "limit_price real,"
                            + "order_id text PRIMARY KEY,"
                            + "ordered_quantity integer,"
                            + "placed_time integer,"
                            + "symbol text"
                    + ");"
            );
            portfolioDataFetcher.addOnPortfolioDataFetchCompletionHandler(this);
            portfolioDataFetcher.addOnPositionLotsUpdateHandler(this);
            LOG.info("Initialized EtradeBuyOrderCreator, haltBuyOrderCashBalance={} idealLotSize={} "
                            + "maxBuyOrdersPerSymbolPerDay={} minLotSize={}",
                    haltBuyOrderCashBalance, idealLotSize, maxBuyOrdersPerSymbolPerDay, minLotSize);
        } catch (SQLException e) {
            LOG.error("Failed to create 'placed_etrade_order' table", e);
        }
    }

    void cachePlacedBuyOrder(String symbol, Order order) {
        placedBuyOrderCache.get(symbol).put(order.getOrderId(), order);
    }

    void enableNewSymbol(String symbol) {
        if (!placedBuyOrderCache.containsKey(symbol)) {
            placedBuyOrderCache.put(symbol, CacheBuilder.newBuilder()
                                                        .expireAfterWrite(24, TimeUnit.HOURS)
                                                        .build());
        }
    }

    long getBuyOrdersCreatedInLast24Hours(String symbol) {
        return placedBuyOrderCache.get(symbol).size();
    }

    /**
     * After portfolio data fetching is complete, we look for buying enabled symbols that don't have any lots.
     * For any that are found, we submit a runnable to buy a lot in a separate thread.
     *
     * @param timeFetchStarted
     * @param timeFetchStopped
     * @param totals
     */
    @Override
    public void handlePortfolioDataFetchCompletion(long timeFetchStarted,
                                                   long timeFetchStopped,
                                                   PortfolioResponse.Totals totals) {
        // Some problem on E*Trades' end might cause portfolio data fetching to take longer than the data's expiry.
        // If that happens, we should not make any buying decisions because we can't trust the quality of our data.
        long fetchDurationSeconds = (timeFetchStopped - timeFetchStarted) / 1000L;
        if (fetchDurationSeconds > portfolioDataFetcher.getPortfolioDataExpirationSeconds()) {
            LOG.warn("Skipping buy order creation, fetchDurationSeconds exceeded portfolioDataExpirationSeconds, "
                            + "timeFetchStarted={}, timeFetchStopped={} fetchDurationSeconds={} symbol={}",
                    timeFetchStarted,
                    timeFetchStopped,
                    fetchDurationSeconds,
                    portfolioDataFetcher.getPortfolioDataExpirationSeconds()
            );
            return;
        }
        LOG.debug("Checking for buying enabled symbols with no lots");
        int freshnessThreshold = (int) (
                (System.currentTimeMillis() / 1000L) - portfolioDataFetcher.getPortfolioDataExpirationSeconds()
        );
        for (String symbol : buyOrderEnabledSymbols) {
            // TODO:
            // - How do we know if a cache miss is not due to data fetching or server side data quality problems?
            // - One option could be to build and maintain an internal representation of what the portfolio should
            //   look like. For example, if we haven't sold any lots, we should have the same number of lots after
            //   fetching data. If that is not the case, we should assume our data is not reliable.
            LotCountPreparedStatementCallback callback = new LotCountPreparedStatementCallback(
                    symbol, freshnessThreshold, totals);
            try {
                DB.executePreparedQuery(
                        "SELECT COUNT(*) FROM etrade_lot WHERE symbol == ? AND updated_time > ?;",
                        callback);
            } catch (SQLException e) {
                LOG.error("Failed execute: {}", callback.getStatement(), e);
            }
        }
    }

    @Override
    public void handlePositionLotsUpdate(String symbol,
                                         PortfolioResponse.Totals totals) {
        if (isBuyOrderCreationEnabled(symbol)) {
            executor.submit(new OnPositionLotsUpdateRunnable(symbol, totals));
        }
    }

    boolean isBelowMaxBuyOrdersPerDayLimit(String symbol) {
        return placedBuyOrderCache.get(symbol).size() < maxBuyOrdersPerSymbolPerDay;
    }

    boolean isBuyOrderCreationEnabled(String symbol) {
        return buyOrderEnabledSymbols.contains(symbol);
    }

    /**
     * Intended for testing only. Returns a BuyOrderRunnable that could be spied on.
     *
     * @param symbol
     * @param totals
     * @return BuyOrderRunnable object
     */
    BuyOrderRunnable newBuyOrderRunnable(String symbol, PortfolioResponse.Totals totals) {
        return new BuyOrderRunnable(symbol, totals);
    }

    OrderDetail newBuyOrderDetailFromLastPrice(String symbol, float lastPrice) {
        OrderDetail.Product product = new OrderDetail.Product();
        product.setSecurityType("EQ");
        product.setSymbol(symbol);

        OrderDetail.Instrument instrument = new OrderDetail.Instrument();
        instrument.setOrderAction("BUY");
        instrument.setProduct(product);
        instrument.setQuantityType("QUANTITY");
        long quantity = quantityFromLastPrice(lastPrice);
        LOG.debug("Preparing buy order, symbol={} quantity={} estimatedLotSize={}",
                symbol, quantity, quantity * lastPrice);
        instrument.setQuantity(quantity);

        OrderDetail orderDetail = new OrderDetail();
        orderDetail.setAllOrNone(false);
        orderDetail.newInstrumentList(instrument);
        orderDetail.setOrderTerm("GOOD_FOR_DAY");
        orderDetail.setMarketSession("REGULAR");
        orderDetail.setPriceType("MARKET"); // TODO: Configurable?
        return orderDetail;
    }

    long quantityFromLastPrice(float lastPrice) {
        if (lastPrice >= idealLotSize) {
            return 1L;
        } else {
            long quantity = Math.round(idealLotSize / lastPrice);
            if (quantity * lastPrice < minLotSize) {
                quantity++;
            }
            return quantity;
        }
    }

    void setEmailHelper(EmailHelper emailHelper) {
        this.emailHelper = emailHelper;
    }

    void setHaltBuyOrderCashBalance(Long haltBuyOrderCashBalance) {
        this.haltBuyOrderCashBalance = haltBuyOrderCashBalance;
    }

    void setMaxBuyOrdersPerSymbolPerDay(long maxBuyOrdersPerSymbolPerDay) {
        this.maxBuyOrdersPerSymbolPerDay = maxBuyOrdersPerSymbolPerDay;
    }

    /**
     * Intended for testing only. Allows overriding with a mocked data fetcher.
     *
     * @param ordersDataFetcher
     */
    void setOrdersDataFetcher(EtradeOrdersDataFetcher ordersDataFetcher) {
        this.ordersDataFetcher = ordersDataFetcher;
    }

    void setPortfolioDataFetcher(EtradePortfolioDataFetcher portfolioDataFetcher) {
        this.portfolioDataFetcher = portfolioDataFetcher;
    }

    /**
     * Functionally abstract but not officially declared to make testing easier.
     */
    class BuyOrderRunnable extends EtradeOrderCreator {

        protected final String symbol;
        protected PortfolioResponse.Totals totals;

        BuyOrderRunnable(String symbol, PortfolioResponse.Totals totals) {
            this.symbol = symbol;
            this.totals = totals;
        }

        public boolean canProceedWithBuyOrderCreation(Float lastPrice) {
            if (!canProceedWithBuyOrderCreation()) {
                return false;
            }
            String constraintsConfigKey = "etrade.buyOrderCreationConstraints." + symbol;
            if (CONFIG.hasPath(constraintsConfigKey)) {
                String maxPriceConfigKey = constraintsConfigKey + ".maxPrice";
                if (CONFIG.hasPath(maxPriceConfigKey)) {
                    float maxPrice = (float) CONFIG.getDouble(maxPriceConfigKey);
                    if (lastPrice > maxPrice) {
                        LOG.debug("Skipping buy order creation, lastPrice above maxPrice, symbol={}, lastPrice={}, maxPrice={}",
                                symbol, lastPrice, maxPrice);
                        return false;
                    }
                }
                String minPriceConfigKey = constraintsConfigKey + ".minPrice";
                float minPrice = 1F;
                if (CONFIG.hasPath(minPriceConfigKey)) {
                    minPrice = (float) CONFIG.getDouble(minPriceConfigKey);
                }
                if (lastPrice < minPrice) {
                    LOG.debug("Skipping buy order creation, lastPrice below minPrice, symbol={}, lastPrice={}, minPrice={}",
                            symbol, lastPrice, minPrice);
                    return false;
                }
            }
            return true;
        }

        public boolean canProceedWithBuyOrderCreation() {
            if (isEmbargoedTimeWindow()) {
                return false;
            }

            Long lastSuccessfulFetchTimeMillis = ordersDataFetcher.getLastSuccessfulFetchTimeMillis();
            if (lastSuccessfulFetchTimeMillis == null) {
                LOG.debug("Skipping buy order creation, orders data fetch has not occurred, symbol={}", symbol);
                return false;
            }
            long currentTimeMillis = System.currentTimeMillis();
            long deltaMillis = currentTimeMillis - lastSuccessfulFetchTimeMillis;
            long thresholdMillis = ordersDataFetcher.getOrdersDataExpirationSeconds() * 1000L;
            if (deltaMillis > thresholdMillis) {
                LOG.warn("Skipping buy order creation, due to orders data staleness, "
                                + "lastSuccessfulFetchTimeMillis={}, deltaMillis={} thresholdMillis={} symbol={}",
                        lastSuccessfulFetchTimeMillis, deltaMillis, thresholdMillis, symbol);
                return false;
            }
            Map<String, List<Order>> symbolToOrdersIndex = ordersDataFetcher.getSymbolToBuyOrdersIndex();
            if (symbolToOrdersIndex.containsKey(symbol)) {
                LOG.debug("Skipping buy order creation, a buy order already exists, symbol={}", symbol);
                return false;
            }
            if (totals.getCashBalance() < haltBuyOrderCashBalance) {
                LOG.info("Skipping buy order creation, cashBalance below haltBuyOrderCashBalance {} < {}, symbol={}",
                        totals.getCashBalance(), haltBuyOrderCashBalance, symbol);
                return false;
            }
            if (!isBelowMaxBuyOrdersPerDayLimit(symbol)) {
                LOG.debug("Skipping buy order creation, {} buy orders have been created in the last 24 hours, symbol={}",
                        getBuyOrdersCreatedInLast24Hours(symbol), symbol);
                return false;
            }
            return true;
        }

        int currentDayOfWeek(OffsetDateTime now) {
            return now.getDayOfWeek().getValue();
        }

        int currentHour(OffsetDateTime now) {
            return now.getHour();
        }

        int currentMinute(OffsetDateTime now) {
            return now.getMinute();
        }

        public boolean isEmbargoedTimeWindow() {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            int currentDayOfWeek = this.currentDayOfWeek(now);
            if (currentDayOfWeek < buyOrderCreationStartDayOfWeek
                    || currentDayOfWeek >= buyOrderCreationStopDayOfWeek) {
                LOG.debug( "Skipping buy order creation, currentDayOfWeek={}, "
                                + "buyOrderCreationStartDayOfWeek={}, buyOrderCreationStopDayOfWeek={}",
                        currentDayOfWeek, buyOrderCreationStartDayOfWeek, buyOrderCreationStopDayOfWeek
                );
                return true;
            }
            int currentHour = this.currentHour(now);
            int currentMinute = this.currentMinute(now);
            if (currentHour < buyOrderCreationStartHour
                    || (currentHour == buyOrderCreationStartHour && currentMinute < buyOrderCreationStartMinute)
                    || currentHour >= buyOrderCreationStopHour) {
                LOG.debug( "Skipping buy order creation, currentHour={} currentMinute={} "
                                + "buyOrderCreationStartHour={} buyOrderCreationStartMinute={} buyOrderCreationStopHour={}",
                        currentHour, currentMinute,
                        buyOrderCreationStartHour, buyOrderCreationStartMinute, buyOrderCreationStopHour
                );
                return true;
            }
            return false;
        }

        @Override
        public void run() {
        }
    }

    class InitialBuyOrderRunnable extends BuyOrderRunnable {

        InitialBuyOrderRunnable(String symbol, PortfolioResponse.Totals totals) {
            super(symbol, totals);
        }

        @Override
        public void run() {
            SecurityContext securityContext = getRestTemplateFactory().getSecurityContext();
            if (!securityContext.isInitialized()) {
                LOG.warn("SecurityContext not initialized, please go to /etrade/authorize");
                return;
            }

            float lastTradedPrice;
            try {
                Message quoteMessage = new Message();
                quoteMessage.setRequiresOauth(true);
                quoteMessage.setHttpMethod("GET");
                quoteMessage.setUrl(getApiConfig().getQuoteUrl() + symbol);
                setOAuthHeader(securityContext, quoteMessage);

                ResponseEntity<QuoteResponse> quoteResponseEntity =
                        getRestTemplateFactory()
                                .newCustomRestTemplate().doGet(quoteMessage, QuoteResponse.class);
                QuoteResponse quoteResponse = quoteResponseEntity.getBody();
                if (quoteResponse == null) {
                    throw new RuntimeException("Empty response");
                }
                lastTradedPrice = quoteResponse.getQuoteDataList().get(0).getAllQuoteDetails().getLastTrade();
                LOG.debug("Fetched quote, symbol={}, lastTradedPrice={}", symbol, lastTradedPrice);
            } catch (Exception e) {
                LOG.debug("Skipping buy order creation due to failure to fetch quote, symbol={}", symbol, e);
                return;
            }
            if (canProceedWithBuyOrderCreation(lastTradedPrice)) {
                try {
                    // Send notification
                    emailHelper.sendMessage(
                            "Did not find any lots",
                            String.format("%s: lastTradedPrice=%f", symbol, lastTradedPrice));
                    // Create buy order
                    placeOrder(
                            securityContext,
                            UUID.randomUUID().toString().substring(0, 8),
                            newBuyOrderDetailFromLastPrice(symbol, lastTradedPrice));
                } catch (Exception e) {
                    LOG.debug("Failed to create buy orders, symbol={}", symbol, e);
                }
            }
        }
    }

    class LotCountPreparedStatementCallback implements SqliteDatabase.PreparedStatementCallback {

        private final int freshnessThreshold;
        private final String symbol;
        private final PortfolioResponse.Totals totals;

        private PreparedStatement statement;

        LotCountPreparedStatementCallback(String symbol, int freshnessThreshold, PortfolioResponse.Totals totals) {
            this.freshnessThreshold = freshnessThreshold;
            this.symbol = symbol;
            this.totals = totals;
        }

        @Override
        public void call(PreparedStatement stmt) throws SQLException {
            this.statement = stmt;
            stmt.setString(1, symbol);
            stmt.setInt(2, freshnessThreshold);
            ResultSet rs = stmt.executeQuery();
            int count = rs.getInt(1);
            rs.close();
            if (count  == 0) {
                LOG.info("Did not find any lots, symbol={}", symbol);
                executor.submit(new InitialBuyOrderRunnable(symbol, totals));
            } else {
                LOG.debug("Skipping buy order creation, found {} lots, symbol={}", count, symbol);
            }
        }

        public PreparedStatement getStatement() {
            return statement;
        }
    }

    class OnPositionLotsUpdateRunnable extends BuyOrderRunnable implements SqliteDatabase.PreparedStatementCallback {

        int freshnessThreshold;
        SecurityContext securityContext;
        PreparedStatement statement;

        OnPositionLotsUpdateRunnable(String symbol, PortfolioResponse.Totals totals) {
            super(symbol, totals);
            securityContext = getRestTemplateFactory().getSecurityContext();
        }

        @Override
        public void call(PreparedStatement stmt) throws SQLException {
            statement = stmt;
            stmt.setString(1, symbol);
            stmt.setInt(2, freshnessThreshold);
            ResultSet rs = stmt.executeQuery();
            float acquiredPrice = rs.getFloat("acquired_price");
            float followPrice = rs.getFloat("follow_price");
            float lastPrice = rs.getFloat("last_price");
            rs.close();
            if (lastPrice < followPrice) {
                LOG.debug("Lowest {} lot, acquiredPrice={}, lastPrice={} followPrice={}",
                        symbol, acquiredPrice, lastPrice, followPrice);
                if (canProceedWithBuyOrderCreation(lastPrice)) {
                    try {
                        // Send notification
                        emailHelper.sendMessage(
                                "Follow threshold breached",
                                String.format(
                                        "%s: acquiredPrice=%f, lastPrice=%f, followPrice=%f",
                                        symbol,
                                        acquiredPrice,
                                        lastPrice,
                                        followPrice));
                        // Create buy order
                        placeOrder(
                                securityContext,
                                UUID.randomUUID().toString().substring(0, 8),
                                newBuyOrderDetailFromLastPrice(symbol, lastPrice));
                    } catch (Exception e) {
                        LOG.debug("Failed to create buy orders, symbol={}", symbol, e);
                    }
                }
            }
        }

        @Override
        public void run() {
            if (!securityContext.isInitialized()) {
                LOG.warn("SecurityContext not initialized, please go to /etrade/authorize");
                return;
            }
            if (getApiConfig().getOrdersPreviewUrl() == null) {
                LOG.warn("Please configure etrade.accountIdKey");
                return;
            }
            freshnessThreshold = (int) (
                    (System.currentTimeMillis() / 1000L) - portfolioDataFetcher.getPortfolioDataExpirationSeconds()
            );
            try {
                DB.executePreparedQuery(
                        "SELECT acquired_price,follow_price,last_price FROM etrade_lot WHERE follow_price == "
                                + "(SELECT min(follow_price) FROM etrade_lot WHERE symbol == ? AND updated_time > ?);",
                        this);
            } catch (SQLException e) {
                LOG.error("Failed execute: {}", statement, e);
            }
        }
    }
}
