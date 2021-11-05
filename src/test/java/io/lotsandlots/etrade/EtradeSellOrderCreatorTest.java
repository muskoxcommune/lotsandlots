package io.lotsandlots.etrade;

import io.lotsandlots.etrade.api.CancelOrderResponse;
import io.lotsandlots.etrade.api.PortfolioResponse;
import io.lotsandlots.etrade.api.PreviewOrderRequest;
import io.lotsandlots.etrade.api.PreviewOrderResponse;
import io.lotsandlots.etrade.oauth.SecurityContext;
import io.lotsandlots.etrade.rest.EtradeRestTemplate;
import io.lotsandlots.etrade.rest.EtradeRestTemplateFactory;
import io.lotsandlots.etrade.rest.Message;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

@Test(groups = {"unit"})
public class EtradeSellOrderCreatorTest {

    @BeforeClass
    public void beforeClass() throws GeneralSecurityException {
        EtradeRestTemplateFactory.init();
    }

    public void testCancelOrder() throws Exception {
        EtradeRestTemplate mockRestTemplate = Mockito.mock(EtradeRestTemplate.class);
        Mockito.doAnswer(invocation -> {
            Message orderCancelMessage = invocation.getArgument(0);
            Assert.assertEquals(orderCancelMessage.getHttpMethod(), "PUT");
            String payload = invocation.getArgument(1);
            Assert.assertEquals(payload, "{\"CancelOrderRequest\":{\"orderId\":1}}");
            ResponseEntity<CancelOrderResponse> mockResponseEntity = Mockito.mock(ResponseEntity.class);
            Mockito.doReturn(new CancelOrderResponse()).when(mockResponseEntity).getBody();
            return mockResponseEntity;
        }).when(mockRestTemplate).doPut(Mockito.any(Message.class), Mockito.anyString(), Mockito.any());
        EtradeRestTemplateFactory mockTemplateFactory = Mockito.mock(EtradeRestTemplateFactory.class);
        Mockito.doReturn(mockRestTemplate).when(mockTemplateFactory).newCustomRestTemplate();

        EtradeSellOrderCreator.SymbolToLotsIndexPutEvent runnable = Mockito.spy(new EtradeSellOrderCreator
                .SymbolToLotsIndexPutEvent("ABC", new ArrayList<>()));
        runnable.setRestTemplateFactory(mockTemplateFactory);
        Mockito.doAnswer(invocation -> null).when(runnable).setOAuthHeader(Mockito.any(), Mockito.any());

        SecurityContext mockSecurityContext = Mockito.mock(SecurityContext.class);
        runnable.cancelOrder(mockSecurityContext, 1L);
        Mockito.verify(mockRestTemplate).doPut(Mockito.any(), Mockito.any(), Mockito.any());
    }

    public void testFetchPreviewOrderResponse() throws Exception {
        EtradeRestTemplate mockRestTemplate = Mockito.mock(EtradeRestTemplate.class);
        Mockito.doAnswer(invocation -> {
            Message orderCancelMessage = invocation.getArgument(0);
            Assert.assertEquals(orderCancelMessage.getHttpMethod(), "POST");
            String payload = invocation.getArgument(1);
            Assert.assertEquals(payload, "{\"PreviewOrderRequest\":{}}");
            ResponseEntity<PreviewOrderResponse> mockResponseEntity = Mockito.mock(ResponseEntity.class);
            Mockito.doReturn(new PreviewOrderResponse()).when(mockResponseEntity).getBody();
            return mockResponseEntity;
        }).when(mockRestTemplate).doPost(Mockito.any(Message.class), Mockito.anyString(), Mockito.any());
        EtradeRestTemplateFactory mockTemplateFactory = Mockito.mock(EtradeRestTemplateFactory.class);
        Mockito.doReturn(mockRestTemplate).when(mockTemplateFactory).newCustomRestTemplate();

        EtradeSellOrderCreator.SymbolToLotsIndexPutEvent runnable = Mockito.spy(new EtradeSellOrderCreator
                .SymbolToLotsIndexPutEvent("ABC", new ArrayList<>()));
        runnable.setRestTemplateFactory(mockTemplateFactory);
        Mockito.doAnswer(invocation -> null).when(runnable).setOAuthHeader(Mockito.any(), Mockito.any());

        PreviewOrderRequest previewOrderRequest = new PreviewOrderRequest();
        SecurityContext mockSecurityContext = Mockito.mock(SecurityContext.class);
        runnable.fetchPreviewOrderResponse(mockSecurityContext, previewOrderRequest);
        Mockito.verify(mockRestTemplate).doPost(Mockito.any(), Mockito.any(), Mockito.any());
    }

    public void testHandleSymbolToLotsIndexPut() {
        ExecutorService mockExecutor = Mockito.mock(ExecutorService.class);
        EtradeSellOrderCreator sellOrderCreator = new EtradeSellOrderCreator();
        sellOrderCreator.setExecutor(mockExecutor);
        sellOrderCreator.handleSymbolToLotsIndexPut("ABC", new ArrayList<>(), new PortfolioResponse.Totals());
        Mockito.verify(mockExecutor).submit(Mockito.any(EtradeSellOrderCreator.SymbolToLotsIndexPutEvent.class));
    }
}
