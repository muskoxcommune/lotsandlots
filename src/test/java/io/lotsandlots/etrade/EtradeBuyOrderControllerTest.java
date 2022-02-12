package io.lotsandlots.etrade;

import io.lotsandlots.etrade.api.PortfolioResponse;
import io.lotsandlots.etrade.api.PositionLotsResponse;
import io.lotsandlots.etrade.model.Order;
import io.lotsandlots.etrade.oauth.SecurityContext;
import io.lotsandlots.etrade.rest.EtradeRestTemplateFactory;
import io.lotsandlots.util.EmailHelper;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
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
        EtradeBuyOrderController orderController = Mockito.spy(new EtradeBuyOrderController());

        EtradeBuyOrderController.BuyOrderRunnable runnable;
        PortfolioResponse.Totals totals;

        ////
        // If a buy order exists, call should return false.
        Map<String, List<Order>> fakedSymbolToBuyOrdersIndex = new HashMap<>();
        fakedSymbolToBuyOrdersIndex.put("TEST1", new LinkedList<>());

        EtradeOrdersDataFetcher ordersDataFetcher = Mockito.mock(EtradeOrdersDataFetcher.class);
        Mockito.doReturn(fakedSymbolToBuyOrdersIndex).when(ordersDataFetcher).getSymbolToBuyOrdersIndex();
        orderController.setOrdersDataFetcher(ordersDataFetcher);

        totals = Mockito.mock(PortfolioResponse.Totals.class);
        runnable = Mockito.spy(orderController.newBuyOrderRunnable("TEST1", totals));
        Assert.assertFalse(runnable.canProceedWithBuyOrderCreation());
        // It should return false before we check our cash balance.
        Mockito.verify(totals, Mockito.times(0)).getCashBalance();

        ////
        // If we've exceeded our cash balance stopping point, call should return false.
        orderController.setHaltBuyOrderCashBalance(0L);

        totals = new PortfolioResponse.Totals();
        totals.setCashBalance(-100.00F);
        runnable = Mockito.spy(orderController.newBuyOrderRunnable("TEST2", totals));
        Assert.assertFalse(runnable.canProceedWithBuyOrderCreation());
        // It should return false before we check # of recent orders placed.
        Mockito.verify(orderController, Mockito.times(0))
                .isBelowMaxBuyOrdersPerDayLimit(Mockito.anyString());

        ////
        // If we've placed buy orders too frequently, call should return false.
        orderController.enableNewSymbol("TEST3");
        orderController.setMaxBuyOrdersPerSymbolPerDay(2L);

        totals = new PortfolioResponse.Totals();
        totals.setCashBalance(100.00F);

        Mockito.doReturn(System.currentTimeMillis()).when(ordersDataFetcher).getLastSuccessfulFetchTimeMillis();
        Mockito.doReturn(180L).when(ordersDataFetcher).getOrdersDataExpirationSeconds();
        runnable = Mockito.spy(orderController.newBuyOrderRunnable("TEST3", totals));
        Assert.assertTrue(runnable.canProceedWithBuyOrderCreation());

        Order order1 = new Order();
        order1.setOrderId(1L);
        orderController.cachePlacedBuyOrder("TEST3", order1);
        Assert.assertTrue(runnable.canProceedWithBuyOrderCreation());

        Order order2 = new Order();
        order2.setOrderId(2L);
        orderController.cachePlacedBuyOrder("TEST3", order2);
        Assert.assertFalse(runnable.canProceedWithBuyOrderCreation());
    }

    public void testHandleSymbolToLotsIndexPut() {
        EmailHelper mockEmailHelper = Mockito.mock(EmailHelper.class);
        EtradeBuyOrderController orderController = Mockito.spy(new EtradeBuyOrderController());
        orderController.enableNewSymbol("TEST1");
        orderController.setEmailHelper(mockEmailHelper);
        orderController.setHaltBuyOrderCashBalance(-10000L);

        EtradeOrdersDataFetcher ordersDataFetcher = Mockito.mock(EtradeOrdersDataFetcher.class);
        Mockito.doReturn(new HashMap<>()).when(ordersDataFetcher).getSymbolToBuyOrdersIndex();
        Mockito.doReturn(System.currentTimeMillis()).when(ordersDataFetcher).getLastSuccessfulFetchTimeMillis();
        Mockito.doReturn(180L).when(ordersDataFetcher).getOrdersDataExpirationSeconds();
        orderController.setOrdersDataFetcher(ordersDataFetcher);

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
        Mockito.doReturn(true).when(orderController).isBuyOrderCreationEnabled(Mockito.anyString());
        orderController.setExecutor(mockExecutor);

        PositionLotsResponse.PositionLot lot = new PositionLotsResponse.PositionLot();
        lot.setFollowPrice(11.00F);
        lot.setRemainingQty(100F);
        lot.setMarketValue(1000.00F);
        List<PositionLotsResponse.PositionLot> lots = new LinkedList<>();
        lots.add(lot);
        PortfolioResponse.Totals totals = new PortfolioResponse.Totals();
        totals.setCashBalance(-1000.00F);
        orderController.handleSymbolToLotsIndexPut("TEST1", lots, totals);
        Mockito.verify(mockExecutor).submit(Mockito.any(EtradeBuyOrderController.SymbolToLotsIndexPutEventRunnable.class));
    }

    public void testHandleSymbolToLotsIndexPutWithNotEnoughCashBalance() {
        EtradeBuyOrderController orderController = Mockito.spy(new EtradeBuyOrderController());
        orderController.setHaltBuyOrderCashBalance(-10000L);

        EtradeOrdersDataFetcher ordersDataFetcher = Mockito.mock(EtradeOrdersDataFetcher.class);
        Mockito.doReturn(new HashMap<>()).when(ordersDataFetcher).getSymbolToBuyOrdersIndex();
        orderController.setOrdersDataFetcher(ordersDataFetcher);

        ExecutorService mockExecutor = Mockito.spy(ExecutorService.class);
        Mockito.doAnswer((Answer<Void>) invocation -> {
            EtradeBuyOrderController.SymbolToLotsIndexPutEventRunnable runnable = Mockito.spy(invocation
                    .getArgument(0, EtradeBuyOrderController.SymbolToLotsIndexPutEventRunnable.class));
            runnable.setRestTemplateFactory(MOCK_TEMPLATE_FACTORY_WITH_INITIALIZED_SECURITY_CONTEXT);
            runnable.run();
            Mockito.verify(orderController, Mockito.times(0))
                    .isBelowMaxBuyOrdersPerDayLimit(Mockito.anyString());
            return null;
        }).when(mockExecutor).submit(Mockito.any(EtradeBuyOrderController.SymbolToLotsIndexPutEventRunnable.class));
        Mockito.doReturn(true).when(orderController).isBuyOrderCreationEnabled(Mockito.anyString());
        orderController.setExecutor(mockExecutor);

        PortfolioResponse.Totals totals = new PortfolioResponse.Totals();
        totals.setCashBalance(-10001.00F);
        orderController.handleSymbolToLotsIndexPut("TEST1", new LinkedList<>(), totals);
        Mockito.verify(mockExecutor).submit(Mockito.any(EtradeBuyOrderController.SymbolToLotsIndexPutEventRunnable.class));
    }

    public void testIsBelowMaxBuyOrdersPerDayLimit() {
        EtradeBuyOrderController orderController = new EtradeBuyOrderController();
        orderController.enableNewSymbol("TEST1");

        Order mockPlacedOrder1 = new Order();
        mockPlacedOrder1.setOrderId(1L);
        orderController.cachePlacedBuyOrder("TEST1", mockPlacedOrder1);
        Assert.assertEquals(orderController.getBuyOrdersCreatedInLast24Hours("TEST1"), 1);
        Assert.assertTrue(orderController.isBelowMaxBuyOrdersPerDayLimit("TEST1"));

        Order mockPlacedOrder2 = new Order();
        mockPlacedOrder2.setOrderId(2L);
        orderController.cachePlacedBuyOrder("TEST1", mockPlacedOrder2);
        Assert.assertEquals(orderController.getBuyOrdersCreatedInLast24Hours("TEST1"), 2);
        Assert.assertTrue(orderController.isBelowMaxBuyOrdersPerDayLimit("TEST1"));

        Order mockPlacedOrder3 = new Order();
        mockPlacedOrder3.setOrderId(3L);
        orderController.cachePlacedBuyOrder("TEST1", mockPlacedOrder3);
        Assert.assertEquals(orderController.getBuyOrdersCreatedInLast24Hours("TEST1"), 3);
        Assert.assertFalse(orderController.isBelowMaxBuyOrdersPerDayLimit("TEST1"));
    }

    public void testIsBuyOrderCreationEnabled() {
        EtradeBuyOrderController orderController = new EtradeBuyOrderController();
        orderController.enableNewSymbol("TEST1");
        Assert.assertTrue(orderController.isBuyOrderCreationEnabled("TEST1"));
    }

    public void testQuantityFromLastPrice() {
        float lastPrice;
        EtradeBuyOrderController orderController = new EtradeBuyOrderController();

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
