package io.lotsandlots.etrade;

import com.google.common.annotations.VisibleForTesting;
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

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EtradeBuyOrderController implements EtradePortfolioDataFetcher.PortfolioDataFetchCompletionHandler,
                                                 EtradePortfolioDataFetcher.SymbolToLotsIndexPutHandler {

    private static final Config CONFIG = ConfigWrapper.getConfig();
    private static final ExecutorService DEFAULT_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final EmailHelper EMAIL = new EmailHelper();
    private static final Logger LOG = LoggerFactory.getLogger(EtradeBuyOrderController.class);

    private final List<String> buyOrderEnabledSymbols = new LinkedList<>();
    private ExecutorService executor;
    private Long haltBuyOrderCashBalance = 0L;

    public EtradeBuyOrderController() {
        executor = DEFAULT_EXECUTOR;
        if (CONFIG.hasPath("etrade.enableBuyOrderCreation")) {
            buyOrderEnabledSymbols.addAll(CONFIG.getStringList("etrade.enableBuyOrderCreation"));
        }
        if (CONFIG.hasPath("etrade.haltBuyOrderCashBalance")) {
            haltBuyOrderCashBalance = CONFIG.getLong("etrade.haltBuyOrderCashBalance");
        }
        LOG.info("Initialized EtradeBuyOrderCreator, haltBuyOrderCashBalance={}", haltBuyOrderCashBalance);
    }

    @Override
    public void handlePortfolioDataFetchCompletion(PortfolioResponse.Totals totals) {
        LOG.debug("Checking buy-order-enabled symbols after portfolio data fetch completed");
        Map<String, List<PositionLotsResponse.PositionLot>> symbolToLotsIndex =
                EtradePortfolioDataFetcher.getSymbolToLotsIndex();
        for (String symbol : buyOrderEnabledSymbols) {
            if (!symbolToLotsIndex.containsKey(symbol)) {
                LOG.info("Did not find any lots, symbol={}", symbol);
                executor.submit(new InitialBuyOrderRunnable(symbol, totals, haltBuyOrderCashBalance));
            }
        }
    }

    @Override
    public void handleSymbolToLotsIndexPut(String symbol,
                                           List<PositionLotsResponse.PositionLot> lots,
                                           PortfolioResponse.Totals totals) {
        if (isBuyOrderCreationEnabled(symbol)) {
            executor.submit(new SymbolToLotsIndexPutEventRunnable(symbol, lots, totals, haltBuyOrderCashBalance));
        }
    }

    @VisibleForTesting
    boolean isBuyOrderCreationEnabled(String symbol) {
        return buyOrderEnabledSymbols.contains(symbol);
    }

    void setExecutor(ExecutorService executor) {
        this.executor = executor;
    }

    static class InitialBuyOrderRunnable extends EtradeOrderCreator {

        private Long haltBuyOrderCashBalance;
        private final String symbol;
        private PortfolioResponse.Totals totals;

        InitialBuyOrderRunnable(String symbol, PortfolioResponse.Totals totals, Long haltBuyOrderCashBalance) {
            this.symbol = symbol;
            this.totals = totals;
            this.haltBuyOrderCashBalance = haltBuyOrderCashBalance;
        }

        @Override
        public void run() {

        }
    }

    static class SymbolToLotsIndexPutEventRunnable extends EtradeOrderCreator {

        private Long haltBuyOrderCashBalance;
        private List<PositionLotsResponse.PositionLot> lots;
        private final String symbol;
        private PortfolioResponse.Totals totals;

        SymbolToLotsIndexPutEventRunnable(String symbol,
                                          List<PositionLotsResponse.PositionLot> lots,
                                          PortfolioResponse.Totals totals,
                                          Long haltBuyOrderCashBalance) {
            this.symbol = symbol;
            this.lots = lots;
            this.totals = totals;
            this.haltBuyOrderCashBalance = haltBuyOrderCashBalance;
        }

        void setHaltBuyOrderCashBalance(Long haltBuyOrderCashBalance) {
            this.haltBuyOrderCashBalance = haltBuyOrderCashBalance;
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
                    Float lastPrice = lowestPricedLot.getMarketValue() / lowestPricedLot.getRemainingQty();
                    if (lastPrice < lowestPricedLot.getFollowPrice()) {
                        LOG.debug("Lowest {} lot is {}, lastPrice={} followPrice={}",
                                symbol, lowestPricedLot.getPrice(), lastPrice, lowestPricedLot.getFollowPrice());
                        Map<String, List<Order>> symbolToOrdersIndex =
                                EtradeOrdersDataFetcher.getDataFetcher().getSymbolToBuyOrdersIndex();
                        if (symbolToOrdersIndex.containsKey(symbol)) {
                            LOG.debug("Skipping buy order creation, a buy order already exists");
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
                                OrderDetail.Product product = new OrderDetail.Product();
                                product.setSecurityType("EQ");
                                product.setSymbol(symbol);

                                OrderDetail.Instrument instrument = new OrderDetail.Instrument();
                                instrument.setOrderAction("BUY");
                                instrument.setProduct(product);
                                instrument.setQuantityType("QUANTITY");
                                long quantity;
                                float idealLotSize = 1000F;
                                float minLotSize = 900F;
                                if (lastPrice >= idealLotSize) {
                                    quantity = 1;
                                } else {
                                    float quantityFloat = idealLotSize / lastPrice;
                                    quantity = Math.round(quantityFloat);
                                    if (quantity * lastPrice < minLotSize) {
                                        quantity++;
                                    }
                                }
                                LOG.debug("Preparing buy order, symbol={} quantity={} estimatedLotSize={}",
                                        symbol, quantity, quantity * lastPrice);
                                instrument.setQuantity(quantity);

                                OrderDetail orderDetail = new OrderDetail();
                                orderDetail.setAllOrNone(false);
                                orderDetail.newInstrumentList(instrument);
                                orderDetail.setOrderTerm("GOOD_FOR_DAY");
                                orderDetail.setMarketSession("REGULAR");
                                orderDetail.setPriceType("MARKET"); // TODO: Configurable?
                                placeOrder(securityContext, clientOrderId, orderDetail);
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
