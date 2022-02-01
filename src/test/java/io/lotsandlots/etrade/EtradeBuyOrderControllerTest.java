package io.lotsandlots.etrade;

import io.lotsandlots.etrade.api.PortfolioResponse;
import io.lotsandlots.etrade.oauth.SecurityContext;
import io.lotsandlots.etrade.rest.EtradeRestTemplateFactory;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.security.GeneralSecurityException;
import java.util.LinkedList;
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

    public void testHandleSymbolToLotsIndexPut() {
        EtradeBuyOrderController orderController = Mockito.spy(new EtradeBuyOrderController());
        ExecutorService mockExecutor = Mockito.spy(ExecutorService.class);
        Mockito.doAnswer((Answer<Void>) invocation -> {
            PortfolioResponse.Totals totals = new PortfolioResponse.Totals();
            totals.setCashBalance(-1000.00F);
            EtradeBuyOrderController.SymbolToLotsIndexPutEvent runnable = Mockito.spy(invocation
                    .getArgument(0, EtradeBuyOrderController.SymbolToLotsIndexPutEvent.class));
            runnable.setHaltBuyOrderCashBalance(-10000L);
            runnable.setTotals(totals);
            runnable.setRestTemplateFactory(MOCK_TEMPLATE_FACTORY_WITH_INITIALIZED_SECURITY_CONTEXT);
            runnable.run();
            Mockito.verify(runnable).getLots();
            return null;
        }).when(mockExecutor).submit(Mockito.any(EtradeBuyOrderController.SymbolToLotsIndexPutEvent.class));
        Mockito.doReturn(true).when(orderController).isBuyOrderCreationEnabled(Mockito.anyString());
        orderController.setExecutor(mockExecutor);
        orderController.handleSymbolToLotsIndexPut("TEST1", new LinkedList<>(), new PortfolioResponse.Totals());
        Mockito.verify(mockExecutor).submit(Mockito.any(EtradeBuyOrderController.SymbolToLotsIndexPutEvent.class));
    }

    public void testHandleSymbolToLotsIndexPutWithNotEnoughCashBalance() {
        EtradeBuyOrderController orderController = Mockito.spy(new EtradeBuyOrderController());
        ExecutorService mockExecutor = Mockito.spy(ExecutorService.class);
        Mockito.doAnswer((Answer<Void>) invocation -> {
            PortfolioResponse.Totals totals = new PortfolioResponse.Totals();
            totals.setCashBalance(-10001.00F);
            EtradeBuyOrderController.SymbolToLotsIndexPutEvent runnable = Mockito.spy(invocation
                    .getArgument(0, EtradeBuyOrderController.SymbolToLotsIndexPutEvent.class));
            runnable.setHaltBuyOrderCashBalance(-10000L);
            runnable.setTotals(totals);
            runnable.setRestTemplateFactory(MOCK_TEMPLATE_FACTORY_WITH_INITIALIZED_SECURITY_CONTEXT);
            runnable.run();
            Mockito.verify(runnable, Mockito.times(0)).getLots();
            return null;
        }).when(mockExecutor).submit(Mockito.any(EtradeBuyOrderController.SymbolToLotsIndexPutEvent.class));
        Mockito.doReturn(true).when(orderController).isBuyOrderCreationEnabled(Mockito.anyString());
        orderController.setExecutor(mockExecutor);
        orderController.handleSymbolToLotsIndexPut("TEST1", new LinkedList<>(), new PortfolioResponse.Totals());
        Mockito.verify(mockExecutor).submit(Mockito.any(EtradeBuyOrderController.SymbolToLotsIndexPutEvent.class));
    }
}
