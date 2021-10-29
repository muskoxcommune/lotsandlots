package io.lotsandlots.etrade;

import com.typesafe.config.Config;
import io.lotsandlots.etrade.api.OrdersResponse;
import io.lotsandlots.etrade.api.PortfolioResponse;
import io.lotsandlots.etrade.api.PositionLotsResponse;
import io.lotsandlots.etrade.oauth.SecurityContext;
import io.lotsandlots.etrade.rest.Message;
import io.lotsandlots.util.ConfigWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EtradeSellOrderCreator implements EtradePortfolioDataFetcher.SymbolToLotsIndexPutHandler {

    private static final ExecutorService DEFAULT_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Logger LOG = LoggerFactory.getLogger(EtradeSellOrderCreator.class);

    private static final Config CONFIG = ConfigWrapper.getConfig();
    private static final Boolean CANCEL_ALL_ORDERS_ON_LOTS_ORDERS_MISMATCH = CONFIG.getBoolean(
            "etrade.cancelAllOrdersOnLotsOrdersMismatch");

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

    static class SymbolToLotsIndexPutEvent extends EtradeDataFetcher {

        private List<PositionLotsResponse.PositionLot> lotList;
        private final String symbol;

        SymbolToLotsIndexPutEvent(String symbol,
                                  List<PositionLotsResponse.PositionLot> lotList) {
            this.lotList = lotList;
            this.symbol = symbol;
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
                if (!CANCEL_ALL_ORDERS_ON_LOTS_ORDERS_MISMATCH) {
                    return;
                }
                // Cancel all existing orders
                LOG.info("Canceling {} existing sell orders, symbol={}", orderListSize, symbol);
                for (OrdersResponse.Order order : orderList) {
                    try {
                        Message orderCancelMessage = new Message();
                        orderCancelMessage.setRequiresOauth(true);
                        orderCancelMessage.setHttpMethod("PUT");
                        orderCancelMessage.setUrl(getApiConfig().getOrdersCancelUrl());
                        setOAuthHeader(securityContext, orderCancelMessage);
                    } catch (Exception e) {
                    }
                }
            } else {
                LOG.debug("Found 0 orders for {} lots, symbol={}", lotListSize, symbol);
            }
            // Create orders, then update local caches immediately
            LOG.info("Creating sell orders for {} lots, symbol={}", lotListSize, symbol);
            long timeStartedMillis = System.currentTimeMillis();
            try {
                Message ordersPreviewMessage = new Message();
                ordersPreviewMessage.setRequiresOauth(true);
                ordersPreviewMessage.setHttpMethod("POST");
                ordersPreviewMessage.setUrl(getApiConfig().getOrdersPreviewUrl());
                setOAuthHeader(securityContext, ordersPreviewMessage);
            } catch (Exception e) {
            }
        }
    }
}