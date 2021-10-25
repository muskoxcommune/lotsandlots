package io.lotsandlots.etrade;

import io.lotsandlots.etrade.api.PortfolioResponse;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.testng.annotations.Test;

import java.util.LinkedList;
import java.util.concurrent.ExecutorService;

@Test(groups = {"unit"})
public class EtradeBuyOrderCreatorTest {

    public void testHandlePut() {
        EtradeBuyOrderCreator orderCreator = new EtradeBuyOrderCreator();
        ExecutorService mockExecutor = Mockito.spy(ExecutorService.class);
        Mockito.doAnswer((Answer<Void>) invocation -> {
            PortfolioResponse.Totals totals = new PortfolioResponse.Totals();
            totals.setCashBalance(-1000.00F);
            EtradeBuyOrderCreator.SymbolToLotsIndexPutEvent runnable = Mockito.spy(invocation
                    .getArgument(0, EtradeBuyOrderCreator.SymbolToLotsIndexPutEvent.class));
            runnable.setHaltBuyOrderCashBalance(-10000L);
            runnable.setTotals(totals);
            runnable.run();
            Mockito.verify(runnable).getLots();
            return null;
        }).when(mockExecutor).submit(Mockito.any(EtradeBuyOrderCreator.SymbolToLotsIndexPutEvent.class));
        orderCreator.setExecutor(mockExecutor);
        orderCreator.handlePut("TEST1", new LinkedList<>(), new PortfolioResponse.Totals());
        Mockito.verify(mockExecutor).submit(Mockito.any(EtradeBuyOrderCreator.SymbolToLotsIndexPutEvent.class));
    }

    public void testHandlePutNotEnoughCashBalance() {
        EtradeBuyOrderCreator orderCreator = new EtradeBuyOrderCreator();
        ExecutorService mockExecutor = Mockito.spy(ExecutorService.class);
        Mockito.doAnswer((Answer<Void>) invocation -> {
            PortfolioResponse.Totals totals = new PortfolioResponse.Totals();
            totals.setCashBalance(-10001.00F);
            EtradeBuyOrderCreator.SymbolToLotsIndexPutEvent runnable = Mockito.spy(invocation
                    .getArgument(0, EtradeBuyOrderCreator.SymbolToLotsIndexPutEvent.class));
            runnable.setHaltBuyOrderCashBalance(-10000L);
            runnable.setTotals(totals);
            runnable.run();
            Mockito.verify(runnable, Mockito.times(0)).getLots();
            return null;
        }).when(mockExecutor).submit(Mockito.any(EtradeBuyOrderCreator.SymbolToLotsIndexPutEvent.class));
        orderCreator.setExecutor(mockExecutor);
        orderCreator.handlePut("TEST1", new LinkedList<>(), new PortfolioResponse.Totals());
        Mockito.verify(mockExecutor).submit(Mockito.any(EtradeBuyOrderCreator.SymbolToLotsIndexPutEvent.class));
    }
}
