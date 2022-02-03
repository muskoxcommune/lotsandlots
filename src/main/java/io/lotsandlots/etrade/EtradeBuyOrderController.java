package io.lotsandlots.etrade;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.typesafe.config.Config;
import io.lotsandlots.etrade.api.OrderDetail;
import io.lotsandlots.etrade.api.PortfolioResponse;
import io.lotsandlots.etrade.api.PositionLotsResponse;
import io.lotsandlots.etrade.model.Order;
import io.lotsandlots.etrade.oauth.SecurityContext;
import io.lotsandlots.util.ConfigWrapper;
import io.lotsandlots.util.EmailHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final EmailHelper EMAIL = new EmailHelper();
    private static final Logger LOG = LoggerFactory.getLogger(EtradeBuyOrderController.class);

    private final Map<String, Cache<Long, Order>> placedBuyOrderCache = new HashMap<>();
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

    void setExecutor(ExecutorService executor) {
        this.executor = executor;
    }

    @VisibleForTesting
    public void setHaltBuyOrderCashBalance(Long haltBuyOrderCashBalance) {
        this.haltBuyOrderCashBalance = haltBuyOrderCashBalance;
    }

    class InitialBuyOrderRunnable extends EtradeOrderCreator {

        private final String symbol;
        private PortfolioResponse.Totals totals;

        InitialBuyOrderRunnable(String symbol, PortfolioResponse.Totals totals) {
            this.symbol = symbol;
            this.totals = totals;
        }

        @Override
        public void run() {

        }
    }

    class SymbolToLotsIndexPutEventRunnable extends EtradeOrderCreator {

        private List<PositionLotsResponse.PositionLot> lots;
        private final String symbol;
        private PortfolioResponse.Totals totals;

        SymbolToLotsIndexPutEventRunnable(String symbol,
                                          List<PositionLotsResponse.PositionLot> lots,
                                          PortfolioResponse.Totals totals) {
            this.symbol = symbol;
            this.lots = lots;
            this.totals = totals;
        }

        List<PositionLotsResponse.PositionLot> getLots() {
            return lots;
        }
        void setLots(List<PositionLotsResponse.PositionLot> lots) {
            this.lots = lots;
        }

        void setTotals(PortfolioResponse.Totals totals) {
            this.totals = totals;
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
            if (totals.getCashBalance() < haltBuyOrderCashBalance) {
                LOG.info("CashBalance below haltBuyOrderCashBalance {} < {}",
                        totals.getCashBalance(), haltBuyOrderCashBalance);
            } else {
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
                        Map<String, List<Order>> symbolToOrdersIndex =
                                EtradeOrdersDataFetcher.getDataFetcher().getSymbolToBuyOrdersIndex();
                        if (symbolToOrdersIndex.containsKey(symbol)) {
                            LOG.debug("Skipping buy order creation, a buy order already exists");
                        } else if (!isBelowMaxBuyOrdersPerDayLimit(symbol)) {
                            LOG.debug("Skipping buy order creation, {} buy orders have been created "
                                    + "in the last 24 hours", getBuyOrdersCreatedInLast24Hours(symbol));
                        } else {
                            // Send notification
                            EMAIL.sendMessage(
                                    "Follow threshold breached",
                                    String.format(
                                            "%s: lastPrice=%f, lowestPrice=%f, followPrice=%f",
                                            symbol,
                                            lastPrice,
                                            lowestPricedLot.getPrice(),
                                            lowestPricedLot.getFollowPrice()));
                            // Create buy order
                            String clientOrderId = UUID.randomUUID().toString().substring(0, 8);
                            try {
                                Order placedBuyOrder = placeOrder(
                                        securityContext,
                                        clientOrderId,
                                        newBuyOrderDetailFromLastPrice(symbol, lastPrice));
                                cachePlacedBuyOrder(symbol, placedBuyOrder);
                            } catch (Exception e) {
                                LOG.debug("Unable to finish creating buy orders, symbol={}", symbol, e);
                            }
                        }
                    }
                }
            }
        }
    }
}
