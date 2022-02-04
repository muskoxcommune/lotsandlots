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
    private static final ExecutorService DEFAULT_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Logger LOG = LoggerFactory.getLogger(EtradeBuyOrderController.class);

    private final Map<String, Cache<Long, Order>> placedBuyOrderCache = new HashMap<>();

    private EmailHelper emailHelper = new EmailHelper();
    private ExecutorService executor;
    private long haltBuyOrderCashBalance = 0L;
    private float idealLotSize = 1000F;
    private long maxBuyOrdersPerSymbolPerDay = 3L;
    private float minLotSize = 900F;

    public EtradeBuyOrderController() {
        executor = DEFAULT_EXECUTOR;
        if (CONFIG.hasPath("etrade.enableBuyOrderCreation")) {
            for (String symbol : CONFIG.getStringList("etrade.enableBuyOrderCreation")) {
                enableNewSymbol(symbol);
            }
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
        LOG.info("Initialized EtradeBuyOrderCreator, haltBuyOrderCashBalance={}", haltBuyOrderCashBalance);
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

    @Override
    public void handlePortfolioDataFetchCompletion(PortfolioResponse.Totals totals) {
        LOG.debug("Checking buy-order-enabled symbols after portfolio data fetch completed");
        Map<String, List<PositionLotsResponse.PositionLot>> symbolToLotsIndex =
                EtradePortfolioDataFetcher.getSymbolToLotsIndex();
        for (String symbol : placedBuyOrderCache.keySet()) {
            if (!symbolToLotsIndex.containsKey(symbol)) {
                LOG.info("Did not find any lots, symbol={}", symbol);
                executor.submit(new InitialBuyOrderRunnable(symbol, totals));
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

    void setExecutor(ExecutorService executor) {
        this.executor = executor;
    }

    void setHaltBuyOrderCashBalance(Long haltBuyOrderCashBalance) {
        this.haltBuyOrderCashBalance = haltBuyOrderCashBalance;
    }

    abstract class BuyOrderRunnable extends EtradeOrderCreator {

        protected final String symbol;
        protected PortfolioResponse.Totals totals;

        BuyOrderRunnable(String symbol, PortfolioResponse.Totals totals) {
            this.symbol = symbol;
            this.totals = totals;
        }

        public boolean canProceedWithBuyOrderCreation() {
            Map<String, List<Order>> symbolToOrdersIndex =
                    EtradeOrdersDataFetcher.getDataFetcher().getSymbolToBuyOrdersIndex();
            if (symbolToOrdersIndex.containsKey(symbol)) {
                LOG.debug("Skipping buy order creation, a buy order already exists");
                return false;
            }
            if (totals.getCashBalance() < haltBuyOrderCashBalance) {
                LOG.info("Skipping buy order creation, cashBalance below haltBuyOrderCashBalance {} < {}",
                        totals.getCashBalance(), haltBuyOrderCashBalance);
                return false;
            }
            if (!isBelowMaxBuyOrdersPerDayLimit(symbol)) {
                LOG.debug("Skipping buy order creation, {} buy orders have been created "
                        + "in the last 24 hours", getBuyOrdersCreatedInLast24Hours(symbol));
                return false;
            }
            return true;
        }

        void setTotals(PortfolioResponse.Totals totals) {
            this.totals = totals;
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
            if (canProceedWithBuyOrderCreation()) {
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
                    float lastTradedPrice = quoteResponse.getQuoteDataList().get(0).getAllQuoteDetails().getLastTrade();
                    LOG.debug("Fetched quote, symbol={}, lastTradedPrice={}", symbol, lastTradedPrice);
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

        private List<PositionLotsResponse.PositionLot> lots;

        SymbolToLotsIndexPutEventRunnable(String symbol,
                                          List<PositionLotsResponse.PositionLot> lots,
                                          PortfolioResponse.Totals totals) {
            super(symbol, totals);
            this.lots = lots;
        }

        List<PositionLotsResponse.PositionLot> getLots() {
            return lots;
        }
        void setLots(List<PositionLotsResponse.PositionLot> lots) {
            this.lots = lots;
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
                if (lastPrice < lowestPricedLot.getFollowPrice()) {
                    LOG.debug("Lowest {} lot is {}, lastPrice={} followPrice={}",
                            symbol, lowestPricedLot.getPrice(), lastPrice, lowestPricedLot.getFollowPrice());
                    if (canProceedWithBuyOrderCreation()) {
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
