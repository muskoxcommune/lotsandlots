package io.lotsandlots.etrade;

import io.lotsandlots.etrade.api.OrdersResponse;
import io.lotsandlots.etrade.api.PortfolioResponse;
import io.lotsandlots.etrade.api.PositionLotsResponse;
import io.lotsandlots.etrade.oauth.EtradeOAuthClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EtradeSellOrderCreator implements
        EtradeOAuthClient, EtradePortfolioDataFetcher.SymbolToLotsIndexPutHandler {

    private static final Logger LOG = LoggerFactory.getLogger(EtradeSellOrderCreator.class);
    private static final ExecutorService DEFAULT_EXECUTOR = Executors.newSingleThreadExecutor();

    private ExecutorService executor;

    public EtradeSellOrderCreator() {
        executor = DEFAULT_EXECUTOR;
        LOG.info("Initialized EtradeSellOrderCreator");
    }

    @Override
    public void handleSymbolToLotsIndexPut(String symbol,
                                           List<PositionLotsResponse.PositionLot> lots,
                                           PortfolioResponse.Totals totals) {
        executor.submit(new SymbolToLotsIndexPutEvent(symbol, lots));
    }

    static class SymbolToLotsIndexPutEvent implements Runnable {

        private final String symbol;
        private List<PositionLotsResponse.PositionLot> lotList;

        SymbolToLotsIndexPutEvent(String symbol,
                                  List<PositionLotsResponse.PositionLot> lotList) {
            this.symbol = symbol;
            this.lotList = lotList;
        }

        @Override
        public void run() {
            int lotListSize = lotList.size();
            Map<String, List<OrdersResponse.Order>> symbolToOrdersIndex =
                    EtradeOrdersDataFetcher.getSymbolToSellOrdersIndex();
            if (symbolToOrdersIndex.containsKey(symbol)) {
                List<OrdersResponse.Order> orderList = symbolToOrdersIndex.get(symbol);
                int orderListSize = orderList.size();
                LOG.debug("Found {} orders for {} lots, symbol={}", orderListSize, lotListSize, symbol);
                if (orderListSize == lotListSize) {
                    return;
                }
            } else {
                LOG.debug("Found 0 orders for {} lots, symbol={}", lotListSize, symbol);
            }
            LOG.info("Creating 0 orders for {} lots, symbol={}", lotListSize, symbol);
            // Create orders
            // Update local caches immediately
        }
    }
}