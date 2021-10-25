package io.lotsandlots.etrade;

import com.typesafe.config.Config;
import io.lotsandlots.etrade.api.PortfolioResponse;
import io.lotsandlots.etrade.api.PositionLotsResponse;
import io.lotsandlots.etrade.oauth.EtradeOAuthClient;
import io.lotsandlots.util.ConfigWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
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
    public void handlePut(String symbol,
                          List<PositionLotsResponse.PositionLot> lots,
                          PortfolioResponse.Totals totals) {
        executor.submit(new SymbolToLotsIndexPutEvent(symbol, lots, totals, haltBuyOrderCashBalance));
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
            LOG.debug("New SymbolToLotsIndexPutEvent, symbol={}", symbol);
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
            }
        }
    }
}
