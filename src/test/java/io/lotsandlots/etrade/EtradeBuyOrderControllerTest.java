package io.lotsandlots.etrade;

import io.lotsandlots.etrade.api.PortfolioResponse;
import io.lotsandlots.etrade.api.PositionLotsResponse;
import io.lotsandlots.etrade.api.QuoteResponse;
import io.lotsandlots.etrade.model.Order;
import io.lotsandlots.etrade.oauth.SecurityContext;
import io.lotsandlots.etrade.rest.EtradeRestTemplate;
import io.lotsandlots.etrade.rest.EtradeRestTemplateFactory;
import io.lotsandlots.etrade.rest.Message;
import io.lotsandlots.util.EmailHelper;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.http.ResponseEntity;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@Test(groups = {"unit"})
public class EtradeBuyOrderControllerTest {

    private static EtradeRestTemplateFactory MOCK_TEMPLATE_FACTORY_WITH_INITIALIZED_SECURITY_CONTEXT =
            Mockito.mock(EtradeRestTemplateFactory.class);

    @BeforeClass
    public void beforeClass() throws GeneralSecurityException {
        SecurityContext mockSecurityContext = Mockito.mock(SecurityContext.class);
        Mockito.doReturn(true).when(mockSecurityContext).isInitialized();
        Mockito.doReturn(mockSecurityContext)
                .when(MOCK_TEMPLATE_FACTORY_WITH_INITIALIZED_SECURITY_CONTEXT).getSecurityContext();
    }

    public void testCanProceedWithBuyOrderCreation() {
        EtradeBuyOrderController orderController;
        EtradeOrdersDataFetcher ordersDataFetcher;
        EtradeBuyOrderController.BuyOrderRunnable runnable;
        Map<String, List<Order>> spiedSymbolToBuyOrdersIndex;
        PortfolioResponse.Totals totals;

        ////
        // If orders data fetching has not been completed yet, call should return false.
        ordersDataFetcher = Mockito.mock(EtradeOrdersDataFetcher.class);
        Mockito.doReturn(null).when(ordersDataFetcher).getLastSuccessfulFetchTimeMillis();

        orderController = Mockito.spy(
                new EtradeBuyOrderController(Mockito.mock(EtradePortfolioDataFetcher.class), ordersDataFetcher));
        orderController.setOrdersDataFetcher(ordersDataFetcher);

        totals = Mockito.mock(PortfolioResponse.Totals.class);
        runnable = Mockito.spy(orderController.newBuyOrderRunnable("BUYING_CHECK_BEFORE_ORDER_FETCH_COMPLETION", totals));
        Assert.assertFalse(runnable.canProceedWithBuyOrderCreation());
        // It should return false before we check for order data fetching delay.
        Mockito.verify(ordersDataFetcher, Mockito.times(0)).getOrdersDataExpirationSeconds();

        ////
        // If orders data is stale, call should return false.
        ordersDataFetcher = Mockito.mock(EtradeOrdersDataFetcher.class);
        Mockito.doReturn(System.currentTimeMillis() - 2000L).when(ordersDataFetcher).getLastSuccessfulFetchTimeMillis();
        Mockito.doReturn(1L).when(ordersDataFetcher).getOrdersDataExpirationSeconds();

        orderController = Mockito.spy(
                new EtradeBuyOrderController(Mockito.mock(EtradePortfolioDataFetcher.class), ordersDataFetcher));
        orderController.setOrdersDataFetcher(ordersDataFetcher);

        totals = Mockito.mock(PortfolioResponse.Totals.class);
        runnable = Mockito.spy(orderController.newBuyOrderRunnable("BUYING_CHECK_AFTER_LONG_ORDERS_FETCH", totals));
        Assert.assertFalse(runnable.canProceedWithBuyOrderCreation());
        // It should return false before we check if an order already exists.
        Mockito.verify(ordersDataFetcher, Mockito.times(0)).getSymbolToBuyOrdersIndex();

        ////
        // If a buy order exists, call should return false.
        spiedSymbolToBuyOrdersIndex = Mockito.spy(new HashMap<>());
        spiedSymbolToBuyOrdersIndex.put("BUYING_CHECK_WITH_ORDER_EXISTING", new LinkedList<>());

        ordersDataFetcher = Mockito.mock(EtradeOrdersDataFetcher.class);
        Mockito.doReturn(System.currentTimeMillis() - 1000L).when(ordersDataFetcher).getLastSuccessfulFetchTimeMillis();
        Mockito.doReturn(2L).when(ordersDataFetcher).getOrdersDataExpirationSeconds();
        Mockito.doReturn(spiedSymbolToBuyOrdersIndex).when(ordersDataFetcher).getSymbolToBuyOrdersIndex();

        orderController = Mockito.spy(
                new EtradeBuyOrderController(Mockito.mock(EtradePortfolioDataFetcher.class), ordersDataFetcher));
        orderController.setOrdersDataFetcher(ordersDataFetcher);

        totals = Mockito.mock(PortfolioResponse.Totals.class);
        runnable = Mockito.spy(orderController.newBuyOrderRunnable("BUYING_CHECK_WITH_ORDER_EXISTING", totals));
        Assert.assertFalse(runnable.canProceedWithBuyOrderCreation());
        // It should check our spiedSymbolToBuyOrdersIndex.
        Mockito.verify(spiedSymbolToBuyOrdersIndex).containsKey(Mockito.anyString());
        // It should return false before we check our cash balance.
        Mockito.verify(totals, Mockito.times(0)).getCashBalance();

        ////
        // If we've exceeded our cash balance stopping point, call should return false.
        spiedSymbolToBuyOrdersIndex = Mockito.spy(new HashMap<>());

        ordersDataFetcher = Mockito.mock(EtradeOrdersDataFetcher.class);
        Mockito.doReturn(System.currentTimeMillis() - 1000L).when(ordersDataFetcher).getLastSuccessfulFetchTimeMillis();
        Mockito.doReturn(2L).when(ordersDataFetcher).getOrdersDataExpirationSeconds();
        Mockito.doReturn(spiedSymbolToBuyOrdersIndex).when(ordersDataFetcher).getSymbolToBuyOrdersIndex();

        orderController = Mockito.spy(
                new EtradeBuyOrderController(Mockito.mock(EtradePortfolioDataFetcher.class), ordersDataFetcher));
        orderController.setHaltBuyOrderCashBalance(0L);
        orderController.setOrdersDataFetcher(ordersDataFetcher);

        totals = new PortfolioResponse.Totals();
        totals.setCashBalance(-100.00F);
        runnable = Mockito.spy(orderController.newBuyOrderRunnable("BUYING_CHECK_WHILE_OVER_LIMIT", totals));
        Assert.assertFalse(runnable.canProceedWithBuyOrderCreation());
        // It should check our spiedSymbolToBuyOrdersIndex.
        Mockito.verify(spiedSymbolToBuyOrdersIndex).containsKey(Mockito.anyString());
        // It should return false before we check # of recent orders placed.
        Mockito.verify(orderController, Mockito.times(0))
                .isBelowMaxBuyOrdersPerDayLimit(Mockito.anyString());

        ////
        // If we've placed buy orders too frequently, call should return false.
        orderController.enableNewSymbol("BUYING_CHECK_AFTER_TOO_MANY_BUYS");
        orderController.setMaxBuyOrdersPerSymbolPerDay(2L);

        totals = new PortfolioResponse.Totals();
        totals.setCashBalance(100.00F);

        Mockito.doReturn(System.currentTimeMillis()).when(ordersDataFetcher).getLastSuccessfulFetchTimeMillis();
        Mockito.doReturn(180L).when(ordersDataFetcher).getOrdersDataExpirationSeconds();
        runnable = Mockito.spy(orderController.newBuyOrderRunnable("BUYING_CHECK_AFTER_TOO_MANY_BUYS", totals));
        Assert.assertTrue(runnable.canProceedWithBuyOrderCreation());

        Order order1 = new Order();
        order1.setOrderId(1L);
        orderController.cachePlacedBuyOrder("BUYING_CHECK_AFTER_TOO_MANY_BUYS", order1);
        Assert.assertTrue(runnable.canProceedWithBuyOrderCreation());

        Order order2 = new Order();
        order2.setOrderId(2L);
        orderController.cachePlacedBuyOrder("BUYING_CHECK_AFTER_TOO_MANY_BUYS", order2);
        Assert.assertFalse(runnable.canProceedWithBuyOrderCreation());
    }

    public void testHandlePortfolioDataFetchCompletion() {
        ExecutorService mockExecutor;
        EtradeBuyOrderController orderController;
        EtradePortfolioDataFetcher portfolioDataFetcher;
        long timeFetchStarted;
        long timeFetchStopped;

        ////
        // If data fetch took too long, we should not submit an InitialBuyOrderRunnable.

        portfolioDataFetcher = Mockito.mock(EtradePortfolioDataFetcher.class);
        Mockito.doReturn(1L).when(portfolioDataFetcher).getPortfolioDataExpirationSeconds();

        mockExecutor = Mockito.mock(ExecutorService.class);
        orderController = Mockito.spy(
                new EtradeBuyOrderController(portfolioDataFetcher, Mockito.mock(EtradeOrdersDataFetcher.class), mockExecutor));
        orderController.enableNewSymbol("FETCH_COMPLETION_AFTER_LONG_FETCH");
        orderController.setPortfolioDataFetcher(portfolioDataFetcher);

        timeFetchStarted = System.currentTimeMillis();
        timeFetchStopped = timeFetchStarted + 2000L;
        orderController.handlePortfolioDataFetchCompletion(
                timeFetchStarted, timeFetchStopped, new PortfolioResponse.Totals());
        Mockito.verify(mockExecutor, Mockito.times(0))
                .submit(Mockito.any(EtradeBuyOrderController.InitialBuyOrderRunnable.class));

        ////
        // If a buy order already exists, we should not submit an InitialBuyOrderRunnable.

        Map<String, List<PositionLotsResponse.PositionLot>> symbolToLotsIndex = new HashMap<>();
        symbolToLotsIndex.put("FETCH_COMPLETION_WITH_EXISTING_ORDER", new LinkedList<>());

        portfolioDataFetcher = Mockito.mock(EtradePortfolioDataFetcher.class);
        Mockito.doReturn(2L).when(portfolioDataFetcher).getPortfolioDataExpirationSeconds();
        Mockito.doReturn(symbolToLotsIndex).when(portfolioDataFetcher).getSymbolToLotsIndex();

        mockExecutor = Mockito.mock(ExecutorService.class);
        orderController = Mockito.spy(
                new EtradeBuyOrderController(portfolioDataFetcher, Mockito.mock(EtradeOrdersDataFetcher.class), mockExecutor));
        orderController.enableNewSymbol("FETCH_COMPLETION_WITH_EXISTING_ORDER");
        orderController.setPortfolioDataFetcher(portfolioDataFetcher);

        timeFetchStarted = System.currentTimeMillis();
        timeFetchStopped = timeFetchStarted + 1000L;
        orderController.handlePortfolioDataFetchCompletion(
                timeFetchStarted, timeFetchStopped, new PortfolioResponse.Totals());
        Mockito.verify(mockExecutor, Mockito.times(0))
                .submit(Mockito.any(EtradeBuyOrderController.InitialBuyOrderRunnable.class));

        ////
        // If a buy order doesn't exist, we should submit an InitialBuyOrderRunnable.

        EmailHelper mockEmailHelper = Mockito.mock(EmailHelper.class);

        EtradeOrdersDataFetcher ordersDataFetcher = Mockito.mock(EtradeOrdersDataFetcher.class);
        Mockito.doReturn(System.currentTimeMillis()).when(ordersDataFetcher).getLastSuccessfulFetchTimeMillis();
        Mockito.doReturn(120L).when(ordersDataFetcher).getOrdersDataExpirationSeconds();

        portfolioDataFetcher = Mockito.mock(EtradePortfolioDataFetcher.class);
        Mockito.doReturn(120L).when(portfolioDataFetcher).getPortfolioDataExpirationSeconds();
        Mockito.doReturn(new HashMap<>()).when(portfolioDataFetcher).getSymbolToLotsIndex();

        mockExecutor = Mockito.mock(ExecutorService.class);
        Mockito.doAnswer(invocation -> {

            ////
            // When an InitialBuyOrderRunnable is submitted, we should send an email and place an order.

            EtradeBuyOrderController.InitialBuyOrderRunnable runnable = Mockito.spy(invocation
                    .getArgument(0, EtradeBuyOrderController.InitialBuyOrderRunnable.class));
            Mockito.doAnswer(inv -> null).when(runnable).setOAuthHeader(Mockito.any(), Mockito.any());

            SecurityContext mockSecurityContext = Mockito.mock(SecurityContext.class);
            Mockito.doReturn(true).when(mockSecurityContext).isInitialized();

            QuoteResponse quoteResponse = new QuoteResponse();
            QuoteResponse.AllQuoteDetails quoteDetails = new QuoteResponse.AllQuoteDetails();
            quoteDetails.setLastTrade(100.00F);
            QuoteResponse.QuoteData quoteData = new QuoteResponse.QuoteData();
            quoteData.setAllQuoteDetails(quoteDetails);
            List<QuoteResponse.QuoteData> quoteDataList = new LinkedList<>();
            quoteDataList.add(quoteData);
            quoteResponse.setQuoteDataList(quoteDataList);

            ResponseEntity<QuoteResponse> responseResponseEntity = Mockito.mock(ResponseEntity.class);
            Mockito.doReturn(quoteResponse).when(responseResponseEntity).getBody();

            EtradeRestTemplate mockQuoteRestTemplate = Mockito.mock(EtradeRestTemplate.class);
            Mockito.doReturn(responseResponseEntity).when(mockQuoteRestTemplate).doGet(Mockito.any(Message.class), Mockito.any());

            EtradeRestTemplateFactory mockRestTemplateFactory = Mockito.mock(EtradeRestTemplateFactory.class);
            Mockito.doReturn(mockSecurityContext).when(mockRestTemplateFactory).getSecurityContext();
            Mockito.doReturn(mockQuoteRestTemplate).when(mockRestTemplateFactory).newCustomRestTemplate();

            runnable.setRestTemplateFactory(mockRestTemplateFactory);
            runnable.run();
            Mockito.verify(mockEmailHelper).sendMessage(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(runnable).placeOrder(Mockito.any(), Mockito.anyString(), Mockito.any());
            return null;
        }).when(mockExecutor).submit(Mockito.any(EtradeBuyOrderController.InitialBuyOrderRunnable.class));

        orderController = Mockito.spy(new EtradeBuyOrderController(portfolioDataFetcher, ordersDataFetcher, mockExecutor));
        orderController.enableNewSymbol("FETCH_COMPLETION_WITHOUT_EXISTING_ORDER");
        orderController.setEmailHelper(mockEmailHelper);
        orderController.setHaltBuyOrderCashBalance(-10000L);
        orderController.setOrdersDataFetcher(ordersDataFetcher);
        orderController.setPortfolioDataFetcher(portfolioDataFetcher);

        PortfolioResponse.Totals totals = new PortfolioResponse.Totals();
        totals.setCashBalance(-1000.00F);

        timeFetchStarted = System.currentTimeMillis();
        timeFetchStopped = timeFetchStarted + 1000L;
        orderController.handlePortfolioDataFetchCompletion(timeFetchStarted, timeFetchStopped, totals);
        Mockito.verify(mockExecutor).submit(Mockito.any(EtradeBuyOrderController.InitialBuyOrderRunnable.class));
    }

    public void testHandleSymbolToLotsIndexPut() {
        EmailHelper mockEmailHelper = Mockito.mock(EmailHelper.class);

        EtradeOrdersDataFetcher ordersDataFetcher = Mockito.mock(EtradeOrdersDataFetcher.class);
        Mockito.doReturn(new HashMap<>()).when(ordersDataFetcher).getSymbolToBuyOrdersIndex();
        Mockito.doReturn(System.currentTimeMillis()).when(ordersDataFetcher).getLastSuccessfulFetchTimeMillis();
        Mockito.doReturn(180L).when(ordersDataFetcher).getOrdersDataExpirationSeconds();

        ExecutorService mockExecutor = Mockito.spy(ExecutorService.class);
        Mockito.doAnswer((Answer<Void>) invocation -> {
            EtradeBuyOrderController.SymbolToLotsIndexPutEventRunnable runnable = Mockito.spy(invocation
                    .getArgument(0, EtradeBuyOrderController.SymbolToLotsIndexPutEventRunnable.class));
            runnable.setRestTemplateFactory(MOCK_TEMPLATE_FACTORY_WITH_INITIALIZED_SECURITY_CONTEXT);
            runnable.run();
            Mockito.verify(mockEmailHelper).sendMessage(Mockito.anyString(), Mockito.anyString());
            Mockito.verify(runnable).placeOrder(Mockito.any(), Mockito.anyString(), Mockito.any());
            return null;
        }).when(mockExecutor).submit(Mockito.any(EtradeBuyOrderController.SymbolToLotsIndexPutEventRunnable.class));

        EtradeBuyOrderController orderController = Mockito.spy(
                new EtradeBuyOrderController(
                        Mockito.mock(EtradePortfolioDataFetcher.class), ordersDataFetcher, mockExecutor));
        orderController.enableNewSymbol("SYMBOL_TO_LOT_INDEX_PUT");
        orderController.setEmailHelper(mockEmailHelper);
        orderController.setHaltBuyOrderCashBalance(-10000L);
        orderController.setOrdersDataFetcher(ordersDataFetcher);
        Mockito.doReturn(true).when(orderController).isBuyOrderCreationEnabled(Mockito.anyString());

        PositionLotsResponse.PositionLot lot = new PositionLotsResponse.PositionLot();
        lot.setFollowPrice(11.00F);
        lot.setRemainingQty(100F);
        lot.setMarketValue(1000.00F);
        List<PositionLotsResponse.PositionLot> lots = new LinkedList<>();
        lots.add(lot);
        PortfolioResponse.Totals totals = new PortfolioResponse.Totals();
        totals.setCashBalance(-1000.00F);
        orderController.handleSymbolToLotsIndexPut("SYMBOL_TO_LOT_INDEX_PUT", lots, totals);
        Mockito.verify(mockExecutor).submit(Mockito.any(EtradeBuyOrderController.SymbolToLotsIndexPutEventRunnable.class));
    }

    public void testHandleSymbolToLotsIndexPutWithNotEnoughCashBalance() {

        EtradeOrdersDataFetcher ordersDataFetcher = Mockito.mock(EtradeOrdersDataFetcher.class);
        Mockito.doReturn(new HashMap<>()).when(ordersDataFetcher).getSymbolToBuyOrdersIndex();

        ExecutorService mockExecutor = Mockito.spy(ExecutorService.class);
        EtradeBuyOrderController orderController = Mockito.spy(
                new EtradeBuyOrderController(Mockito.mock(EtradePortfolioDataFetcher.class), ordersDataFetcher, mockExecutor));
        Mockito.doAnswer((Answer<Void>) invocation -> {
            EtradeBuyOrderController.SymbolToLotsIndexPutEventRunnable runnable = Mockito.spy(invocation
                    .getArgument(0, EtradeBuyOrderController.SymbolToLotsIndexPutEventRunnable.class));
            runnable.setRestTemplateFactory(MOCK_TEMPLATE_FACTORY_WITH_INITIALIZED_SECURITY_CONTEXT);
            runnable.run();
            Mockito.verify(orderController, Mockito.times(0))
                    .isBelowMaxBuyOrdersPerDayLimit(Mockito.anyString());
            return null;
        }).when(mockExecutor).submit(Mockito.any(EtradeBuyOrderController.SymbolToLotsIndexPutEventRunnable.class));
        orderController.setHaltBuyOrderCashBalance(-10000L);
        orderController.setOrdersDataFetcher(ordersDataFetcher);
        Mockito.doReturn(true).when(orderController).isBuyOrderCreationEnabled(Mockito.anyString());

        PortfolioResponse.Totals totals = new PortfolioResponse.Totals();
        totals.setCashBalance(-10001.00F);
        orderController.handleSymbolToLotsIndexPut("SYMBOL_TO_LOT_INDEX_PUT_WITHOUT_ENOUGH_CASH", new LinkedList<>(), totals);
        Mockito.verify(mockExecutor).submit(Mockito.any(EtradeBuyOrderController.SymbolToLotsIndexPutEventRunnable.class));
    }

    public void testIsBelowMaxBuyOrdersPerDayLimit() {
        EtradeBuyOrderController orderController = new EtradeBuyOrderController(
                Mockito.mock(EtradePortfolioDataFetcher.class), Mockito.mock(EtradeOrdersDataFetcher.class));
        orderController.enableNewSymbol("MAX_ORDER_CHECK");

        Order mockPlacedOrder1 = new Order();
        mockPlacedOrder1.setOrderId(1L);
        orderController.cachePlacedBuyOrder("MAX_ORDER_CHECK", mockPlacedOrder1);
        Assert.assertEquals(orderController.getBuyOrdersCreatedInLast24Hours("MAX_ORDER_CHECK"), 1);
        Assert.assertTrue(orderController.isBelowMaxBuyOrdersPerDayLimit("MAX_ORDER_CHECK"));

        Order mockPlacedOrder2 = new Order();
        mockPlacedOrder2.setOrderId(2L);
        orderController.cachePlacedBuyOrder("MAX_ORDER_CHECK", mockPlacedOrder2);
        Assert.assertEquals(orderController.getBuyOrdersCreatedInLast24Hours("MAX_ORDER_CHECK"), 2);
        Assert.assertTrue(orderController.isBelowMaxBuyOrdersPerDayLimit("MAX_ORDER_CHECK"));

        Order mockPlacedOrder3 = new Order();
        mockPlacedOrder3.setOrderId(3L);
        orderController.cachePlacedBuyOrder("MAX_ORDER_CHECK", mockPlacedOrder3);
        Assert.assertEquals(orderController.getBuyOrdersCreatedInLast24Hours("MAX_ORDER_CHECK"), 3);
        Assert.assertFalse(orderController.isBelowMaxBuyOrdersPerDayLimit("MAX_ORDER_CHECK"));
    }

    public void testIsBuyOrderCreationEnabled() {
        EtradeBuyOrderController orderController = new EtradeBuyOrderController(
                Mockito.mock(EtradePortfolioDataFetcher.class), Mockito.mock(EtradeOrdersDataFetcher.class));
        orderController.enableNewSymbol("IS_BUY_ENABLED_CHECK");
        Assert.assertTrue(orderController.isBuyOrderCreationEnabled("IS_BUY_ENABLED_CHECK"));
    }

    public void testQuantityFromLastPrice() {
        float lastPrice;
        EtradeBuyOrderController orderController = new EtradeBuyOrderController(
                Mockito.mock(EtradePortfolioDataFetcher.class), Mockito.mock(EtradeOrdersDataFetcher.class));

        lastPrice = 11F;
        Assert.assertEquals(orderController.quantityFromLastPrice(lastPrice), 91L);

        lastPrice = 400F;
        Assert.assertEquals(orderController.quantityFromLastPrice(lastPrice), 3L);

        lastPrice = 800F;
        Assert.assertEquals(orderController.quantityFromLastPrice(lastPrice), 2L);

        lastPrice = 901F;
        Assert.assertEquals(orderController.quantityFromLastPrice(lastPrice), 1L);

        lastPrice = 3000F;
        Assert.assertEquals(orderController.quantityFromLastPrice(lastPrice), 1L);
    }
}
