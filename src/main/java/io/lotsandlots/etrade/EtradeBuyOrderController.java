package io.lotsandlots.etrade;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.annotations.VisibleForTesting;
import com.typesafe.config.Config;
import io.lotsandlots.etrade.api.OrderDetail;
import io.lotsandlots.etrade.api.PortfolioResponse;
import io.lotsandlots.etrade.api.PositionLotsResponse;
import io.lotsandlots.etrade.api.PreviewOrderRequest;
import io.lotsandlots.etrade.api.PreviewOrderResponse;
import io.lotsandlots.etrade.model.Order;
import io.lotsandlots.etrade.oauth.SecurityContext;
import io.lotsandlots.util.ConfigWrapper;
import io.lotsandlots.util.EmailHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EtradeBuyOrderController implements EtradePortfolioDataFetcher.SymbolToLotsIndexPutHandler {

    private static final Config CONFIG = ConfigWrapper.getConfig();
    private static final ExecutorService DEFAULT_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final EmailHelper EMAIL = new EmailHelper();
    private static final Logger LOG = LoggerFactory.getLogger(EtradeBuyOrderController.class);

    private ExecutorService executor;
    private Long haltBuyOrderCashBalance = 0L;

    public EtradeBuyOrderController() {
        executor = DEFAULT_EXECUTOR;
        if (CONFIG.hasPath("etrade.haltBuyOrderCashBalance")) {
            haltBuyOrderCashBalance = CONFIG.getLong("etrade.haltBuyOrderCashBalance");
        }
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
        if (CONFIG.hasPath("etrade.enableBuyOrderCreation")) {
            return CONFIG.getStringList("etrade.enableBuyOrderCreation").contains(symbol);
        } else {
            return false;
        }
    }

    void setExecutor(ExecutorService executor) {
        this.executor = executor;
    }

    static class SymbolToLotsIndexPutEvent extends EtradeOrderCreator {

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

        PreviewOrderRequest newPreviewOrderRequest(Float lastPrice, String clientOrderId) throws JsonProcessingException {
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

            PreviewOrderRequest previewOrderRequest = new PreviewOrderRequest();
            previewOrderRequest.setOrderDetailList(orderDetail);
            previewOrderRequest.setOrderType("EQ");
            previewOrderRequest.setClientOrderId(clientOrderId);
            if (LOG.isDebugEnabled()) {
                LOG.debug("PreviewOrderRequest{}", OBJECT_MAPPER.writeValueAsString(previewOrderRequest));
            }
            return previewOrderRequest;
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
                                PreviewOrderRequest previewOrderRequest = newPreviewOrderRequest(
                                        lastPrice, clientOrderId);
                                PreviewOrderResponse previewOrderResponse = fetchPreviewOrderResponse(
                                        securityContext, previewOrderRequest);
                                int previewIdListSize = previewOrderResponse.getPreviewIdList().size();
                                if (previewIdListSize != 1) {
                                    throw new RuntimeException("Expected 1 previewId, got " + previewIdListSize);
                                }
                                placeOrder(securityContext, clientOrderId, previewOrderRequest, previewOrderResponse);
                            } catch (Exception e) {
                                LOG.debug("Unable to finish creating sell orders, symbol={}", symbol, e);
                            }
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
