package io.lotsandlots.etrade;

import io.lotsandlots.data.SqliteDatabase;
import io.lotsandlots.etrade.api.PortfolioResponse;
import io.lotsandlots.etrade.model.Order;
import io.lotsandlots.etrade.oauth.SecurityContext;
import io.lotsandlots.etrade.rest.EtradeRestTemplateFactory;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Test(groups = {"unit"})
public class EtradeBuyOrderControllerTest {

    private static final SqliteDatabase DB = SqliteDatabase.getInstance();

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
        Mockito.doReturn(false).when(runnable).isEmbargoedTimeWindow();
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
        Mockito.doReturn(false).when(runnable).isEmbargoedTimeWindow();
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
        Mockito.doReturn(false).when(runnable).isEmbargoedTimeWindow();
        Assert.assertTrue(runnable.canProceedWithBuyOrderCreation());

        Order order1 = new Order();
        order1.setOrderId(1L);
        orderController.cachePlacedBuyOrder("BUYING_CHECK_AFTER_TOO_MANY_BUYS", order1);
        Assert.assertTrue(runnable.canProceedWithBuyOrderCreation());

        Order order2 = new Order();
        order2.setOrderId(2L);
        orderController.cachePlacedBuyOrder("BUYING_CHECK_AFTER_TOO_MANY_BUYS", order2);
        Assert.assertFalse(runnable.canProceedWithBuyOrderCreation());

        ////
        //
        ordersDataFetcher = Mockito.mock(EtradeOrdersDataFetcher.class);
        // To pass data staleness check
        Mockito.doReturn(10L).when(ordersDataFetcher).getOrdersDataExpirationSeconds();
        Mockito.doReturn(System.currentTimeMillis()).when(ordersDataFetcher).getLastSuccessfulFetchTimeMillis();
        orderController = Mockito.spy(
                new EtradeBuyOrderController(Mockito.mock(EtradePortfolioDataFetcher.class), ordersDataFetcher));
        orderController.enableNewSymbol("BUYING_CHECK_WITH_PRICE_CONSTRAINTS");

        totals = new PortfolioResponse.Totals();
        // To pass cash balance check
        totals.setCashBalance(100.00F);

        runnable = Mockito.spy(orderController.newBuyOrderRunnable("BUYING_CHECK_WITH_PRICE_CONSTRAINTS", totals));
        // To pass trading window check
        Mockito.doReturn(false).when(runnable).isEmbargoedTimeWindow();

        Assert.assertTrue(runnable.canProceedWithBuyOrderCreation());
        // Based on maxPrice of 10 in test application.conf
        Assert.assertFalse(runnable.canProceedWithBuyOrderCreation(11F));
        Assert.assertFalse(runnable.canProceedWithBuyOrderCreation(10.01F));
        Assert.assertTrue(runnable.canProceedWithBuyOrderCreation(10F));
        // Based on minPrice of 8 in test application.conf
        Assert.assertTrue(runnable.canProceedWithBuyOrderCreation(8F));
        Assert.assertFalse(runnable.canProceedWithBuyOrderCreation(7.99F));
    }

    public void testHandlePortfolioDataFetchCompletion() throws SQLException {
    }

    public void testHandleSymbolToLotsIndexPut() {
    }

    public void testHandleSymbolToLotsIndexPutWithNotEnoughCashBalance() {
    }

    public void testIsEmbargoedTimeWindow() {
        EtradeBuyOrderController orderController;
        EtradeOrdersDataFetcher ordersDataFetcher;
        EtradeBuyOrderController.BuyOrderRunnable runnable;

        ordersDataFetcher = Mockito.mock(EtradeOrdersDataFetcher.class);
        orderController = Mockito.spy(
                new EtradeBuyOrderController(Mockito.mock(EtradePortfolioDataFetcher.class), ordersDataFetcher));

        runnable = Mockito.spy(orderController.newBuyOrderRunnable("CHECK_EMBARGOED_TIME_WINDOW_DAY_0", null));
        Mockito.doReturn(0).when(runnable).currentDayOfWeek(Mockito.any());
        Assert.assertTrue(runnable.isEmbargoedTimeWindow());

        runnable = Mockito.spy(orderController.newBuyOrderRunnable("CHECK_EMBARGOED_TIME_WINDOW_DAY_1_13_0", null));
        Mockito.doReturn(1).when(runnable).currentDayOfWeek(Mockito.any());
        Mockito.doReturn(13).when(runnable).currentHour(Mockito.any());
        Mockito.doReturn(0).when(runnable).currentMinute(Mockito.any());
        Assert.assertTrue(runnable.isEmbargoedTimeWindow());

        runnable = Mockito.spy(orderController.newBuyOrderRunnable("CHECK_EMBARGOED_TIME_WINDOW_DAY_1_13_30", null));
        Mockito.doReturn(1).when(runnable).currentDayOfWeek(Mockito.any());
        Mockito.doReturn(13).when(runnable).currentHour(Mockito.any());
        Mockito.doReturn(30).when(runnable).currentMinute(Mockito.any());
        Assert.assertFalse(runnable.isEmbargoedTimeWindow());

        runnable = Mockito.spy(orderController.newBuyOrderRunnable("CHECK_EMBARGOED_TIME_WINDOW_DAY_5_19_59", null));
        Mockito.doReturn(5).when(runnable).currentDayOfWeek(Mockito.any());
        Mockito.doReturn(19).when(runnable).currentHour(Mockito.any());
        Mockito.doReturn(59).when(runnable).currentMinute(Mockito.any());
        Assert.assertFalse(runnable.isEmbargoedTimeWindow());

        runnable = Mockito.spy(orderController.newBuyOrderRunnable("CHECK_EMBARGOED_TIME_WINDOW_DAY_5_20_0", null));
        Mockito.doReturn(5).when(runnable).currentDayOfWeek(Mockito.any());
        Mockito.doReturn(20).when(runnable).currentHour(Mockito.any());
        Mockito.doReturn(0).when(runnable).currentMinute(Mockito.any());
        Assert.assertTrue(runnable.isEmbargoedTimeWindow());

        runnable = Mockito.spy(orderController.newBuyOrderRunnable("CHECK_EMBARGOED_TIME_WINDOW_DAY_6", null));
        Mockito.doReturn(6).when(runnable).currentDayOfWeek(Mockito.any());
        Assert.assertTrue(runnable.isEmbargoedTimeWindow());
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
