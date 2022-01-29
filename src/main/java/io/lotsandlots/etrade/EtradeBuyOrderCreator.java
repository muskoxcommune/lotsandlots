package io.lotsandlots.etrade;

import com.google.common.annotations.VisibleForTesting;
import com.typesafe.config.Config;
import io.lotsandlots.etrade.api.PortfolioResponse;
import io.lotsandlots.etrade.api.PositionLotsResponse;
import io.lotsandlots.etrade.model.Order;
import io.lotsandlots.etrade.oauth.EtradeOAuthClient;
import io.lotsandlots.util.ConfigWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EtradeBuyOrderCreator implements
        EtradeOAuthClient, EtradePortfolioDataFetcher.SymbolToLotsIndexPutHandler {

    private static final Config CONFIG = ConfigWrapper.getConfig();
    private static final Logger LOG = LoggerFactory.getLogger(EtradeBuyOrderCreator.class);
    private static final ExecutorService DEFAULT_EXECUTOR = Executors.newSingleThreadExecutor();

    private Long haltBuyOrderCashBalance;
    private ExecutorService executor;

    public EtradeBuyOrderCreator() {
        executor = DEFAULT_EXECUTOR;
        haltBuyOrderCashBalance = CONFIG.getLong("etrade.haltBuyOrderCashBalance");
        LOG.info("Initialized EtradeBuyOrderCreator, haltBuyOrderCashBalance={}", haltBuyOrderCashBalance);
    }

    @Override
    public void handleSymbolToLotsIndexPut(String symbol,
                                           List<PositionLotsResponse.PositionLot> lots,
                                           PortfolioResponse.Totals totals) {
        if (isBuyOrderCreationEnabled(symbol)) {
            executor.submit(new SymbolToLotsIndexPutEvent(symbol, lots, totals, haltBuyOrderCashBalance));
        } else {
            LOG.debug("Skipping buy order creation, symbol={}", symbol);
        }
    }

    @VisibleForTesting
    boolean isBuyOrderCreationEnabled(String symbol) {
        List<String> symbolList = CONFIG.getStringList("etrade.enableBuyOrderCreation");
        if (symbolList == null) {
            return false;
        } else {
            return symbolList.contains(symbol);
        }
    }

    void setExecutor(ExecutorService executor) {
        this.executor = executor;
    }

    static class SymbolToLotsIndexPutEvent implements Runnable {

        private final String symbol;
        private List<PositionLotsResponse.PositionLot> lots;
        private PortfolioResponse.Totals totals;
        private Long haltBuyOrderCashBalance;

        SymbolToLotsIndexPutEvent(String symbol,
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
            if (totals.getCashBalance() > haltBuyOrderCashBalance) {
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
                        if (!symbolToOrdersIndex.containsKey(symbol)) {
                            // Notify configured phone number
                            // Create buy order
                            // Update cache and index
                        }
                    }
                }
            } else {
                LOG.info("CashBalance below haltBuyOrderCashBalance {} < {}",
                        totals.getCashBalance(), haltBuyOrderCashBalance);
            }
        }
    }
}
