package io.lotsandlots.etrade;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.typesafe.config.Config;
import io.lotsandlots.etrade.api.OrderDetail;
import io.lotsandlots.etrade.api.PortfolioResponse;
import io.lotsandlots.etrade.api.PositionLotsResponse;
import io.lotsandlots.etrade.api.QuoteResponse;
import io.lotsandlots.etrade.model.Order;
import io.lotsandlots.etrade.oauth.SecurityContext;
import io.lotsandlots.etrade.rest.Message;
import io.lotsandlots.util.ConfigWrapper;
import io.lotsandlots.util.EmailHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class EtradeBuyOrderController implements EtradePortfolioDataFetcher.PortfolioDataFetchCompletionHandler,
                                                 EtradePortfolioDataFetcher.SymbolToLotsIndexPutHandler {

    private static final Config CONFIG = ConfigWrapper.getConfig();
    private static final ExecutorService DEFAULT_EXECUTOR = Executors.newFixedThreadPool(5);
    private static final Logger LOG = LoggerFactory.getLogger(EtradeBuyOrderController.class);

    private final Map<String, Cache<Long, Order>> placedBuyOrderCache = new HashMap<>();

    private EmailHelper emailHelper = new EmailHelper();
    private EtradePortfolioDataFetcher portfolioDataFetcher;
    private EtradeOrdersDataFetcher ordersDataFetcher;
    private ExecutorService executor;
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
        if (CONFIG.hasPath("etrade.enableBuyOrderCreation")) {
            for (String symbol : CONFIG.getStringList("etrade.enableBuyOrderCreation")) {
                enableNewSymbol(symbol);
            }
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

        this.executor = executor;
        this.ordersDataFetcher = ordersDataFetcher;
        this.portfolioDataFetcher = portfolioDataFetcher;

        portfolioDataFetcher.addDataFetchCompletionHandler(this);
        portfolioDataFetcher.addSymbolToLotsIndexPutHandler(this);

        LOG.info("Initialized EtradeBuyOrderCreator, haltBuyOrderCashBalance={} idealLotSize={} "
                        + "maxBuyOrdersPerSymbolPerDay={} minLotSize={}",
                haltBuyOrderCashBalance, idealLotSize, maxBuyOrdersPerSymbolPerDay, minLotSize);
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
        Map<String, List<PositionLotsResponse.PositionLot>> symbolToLotsIndex = portfolioDataFetcher.getSymbolToLotsIndex();
        for (String symbol : placedBuyOrderCache.keySet()) {
            // TODO:
            // - How do we know if a cache miss is not due to data fetching or server side data quality problems?
            // - One option could be to build and maintain an internal representation of what the portfolio should
            //   look like. For example, if we haven't sold any lots, we should have the same number of lots after
            //   fetching data. If that is not the case, we should assume our data is not reliable.
            if (!symbolToLotsIndex.containsKey(symbol)) {
                LOG.info("Did not find any lots, symbol={}", symbol);
                executor.submit(new InitialBuyOrderRunnable(symbol, totals));
            } else {
                LOG.debug("Skipping buy order creation, found {} lots, symbol={}",
                        symbolToLotsIndex.get(symbol).size(), symbol);
            }
        }
    }

    @Override
    public void handleSymbolToLotsIndexPut(String symbol,
                                           List<PositionLotsResponse.PositionLot> lots,
                                           PortfolioResponse.Totals totals) {
        if (isBuyOrderCreationEnabled(symbol)) {
            executor.submit(new SymbolToLotsIndexPutEventRunnable(symbol, lots, totals));
        }
    }

    boolean isBelowMaxBuyOrdersPerDayLimit(String symbol) {
        return placedBuyOrderCache.get(symbol).size() < maxBuyOrdersPerSymbolPerDay;
    }

    boolean isBuyOrderCreationEnabled(String symbol) {
        return placedBuyOrderCache.containsKey(symbol);
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
                    String clientOrderId = UUID.randomUUID().toString().substring(0, 8);
                    Order placedBuyOrder = placeOrder(
                            securityContext,
                            clientOrderId,
                            newBuyOrderDetailFromLastPrice(symbol, lastTradedPrice));
                    cachePlacedBuyOrder(symbol, placedBuyOrder);
                } catch (Exception e) {
                    LOG.debug("Failed to create buy orders, symbol={}", symbol, e);
                }
            }
        }
    }

    class SymbolToLotsIndexPutEventRunnable extends BuyOrderRunnable {

        private final List<PositionLotsResponse.PositionLot> lots;

        SymbolToLotsIndexPutEventRunnable(String symbol,
                                          List<PositionLotsResponse.PositionLot> lots,
                                          PortfolioResponse.Totals totals) {
            super(symbol, totals);
            this.lots = lots;
        }

        List<PositionLotsResponse.PositionLot> getLots() {
            return lots;
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
            PositionLotsResponse.PositionLot lowestPricedLot = null;
            for (PositionLotsResponse.PositionLot lot : getLots()) {
                if (lowestPricedLot == null || lot.getPrice() < lowestPricedLot.getPrice()) {
                    lowestPricedLot = lot;
                }
            }
            if (lowestPricedLot != null) {
                float lastPrice = lowestPricedLot.getMarketValue() / lowestPricedLot.getRemainingQty();
                LOG.debug("Lowest {} lot is {}, lastPrice={} followPrice={}",
                        symbol, lowestPricedLot.getPrice(), lastPrice, lowestPricedLot.getFollowPrice());
                if (lastPrice < lowestPricedLot.getFollowPrice()) {
                    if (canProceedWithBuyOrderCreation(lastPrice)) {
                        try {
                            // Send notification
                            emailHelper.sendMessage(
                                    "Follow threshold breached",
                                    String.format(
                                            "%s: lastPrice=%f, lowestPrice=%f, followPrice=%f",
                                            symbol,
                                            lastPrice,
                                            lowestPricedLot.getPrice(),
                                            lowestPricedLot.getFollowPrice()));
                            // Create buy order
                            String clientOrderId = UUID.randomUUID().toString().substring(0, 8);
                            Order placedBuyOrder = placeOrder(
                                    securityContext,
                                    clientOrderId,
                                    newBuyOrderDetailFromLastPrice(symbol, lastPrice));
                            cachePlacedBuyOrder(symbol, placedBuyOrder);
                        } catch (Exception e) {
                            LOG.debug("Failed to create buy orders, symbol={}", symbol, e);
                        }
                    }
                }
            }
        }
    }
}
