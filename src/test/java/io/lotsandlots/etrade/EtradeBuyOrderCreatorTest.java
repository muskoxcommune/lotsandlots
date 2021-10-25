package io.lotsandlots.etrade;

import io.lotsandlots.etrade.api.PortfolioResponse;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import java.util.LinkedList;
import java.util.concurrent.ExecutorService;

@Test(groups = {"unit"})
public class EtradeBuyOrderCreatorTest {

    public void testHandlePut() {
        EtradeBuyOrderCreator orderCreator = new EtradeBuyOrderCreator();
        ExecutorService mockExecutor = Mockito.spy(ExecutorService.class);
        orderCreator.setExecutor(mockExecutor);
        orderCreator.handlePut("TEST1", new LinkedList<>(), new PortfolioResponse.Totals());
        Mockito.verify(mockExecutor).submit(Mockito.any(EtradeBuyOrderCreator.SymbolToLotsIndexPutEvent.class));
    }
}
