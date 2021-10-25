package io.lotsandlots.etrade;

import io.lotsandlots.etrade.api.PortfolioResponse;
import io.lotsandlots.etrade.api.PositionLotsResponse;
import io.lotsandlots.etrade.oauth.EtradeOAuthClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EtradeBuyOrderCreator implements
        EtradeOAuthClient, EtradePortfolioDataFetcher.SymbolToLotsIndexPutHandler {

    private static final Logger LOG = LoggerFactory.getLogger(EtradeBuyOrderCreator.class);
    private static final ExecutorService DEFAULT_EXECUTOR = Executors.newSingleThreadExecutor();

    private ExecutorService executor;

    public EtradeBuyOrderCreator() {
        executor = DEFAULT_EXECUTOR;
    }

    @Override
    public void handlePut(String symbol,
                          List<PositionLotsResponse.PositionLot> lots,
                          PortfolioResponse.Totals totals) {
        executor.submit(new SymbolToLotsIndexPutEvent(symbol, lots, totals));
    }

    void setExecutor(ExecutorService executor) {
        this.executor = executor;
    }

    static class SymbolToLotsIndexPutEvent implements Runnable {

        private final List<PositionLotsResponse.PositionLot> lots;
        private final String symbol;
        private final PortfolioResponse.Totals totals;

        SymbolToLotsIndexPutEvent(String symbol,
                                  List<PositionLotsResponse.PositionLot> lots,
                                  PortfolioResponse.Totals totals) {
            this.symbol = symbol;
            this.lots = lots;
            this.totals = totals;
        }

        @Override
        public void run() {
            LOG.debug("New SymbolToLotsIndexPutEvent, symbol={}", symbol);
        }
    }
}
