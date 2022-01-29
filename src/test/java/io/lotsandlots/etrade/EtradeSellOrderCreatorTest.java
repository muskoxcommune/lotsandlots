package io.lotsandlots.etrade;

import io.lotsandlots.etrade.api.*;
import io.lotsandlots.etrade.model.Order;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public void testRun() throws Exception {
        Order order1 = new Order();
        order1.setOrderId(1L);
        List<Order> orderList = new ArrayList<>();
        orderList.add(order1);
        Map<String, List<Order>> symbolToOrdersIndex = Mockito.spy(new HashMap<>());
        symbolToOrdersIndex.put("ABC", orderList);

        EtradeOrdersDataFetcher dataFetcher = Mockito.spy(new EtradeOrdersDataFetcher());
        dataFetcher.setSymbolToSellOrdersIndex(symbolToOrdersIndex);
        EtradeOrdersDataFetcher.setDataFetcher(dataFetcher);

        ApiConfig apiConfig = new ApiConfig();
        apiConfig.setOrdersPreviewUrl("https://baseUrl/orders/keyId/preview");

        SecurityContext mockSecurityContext = Mockito.mock(SecurityContext.class);
        Mockito.doReturn(true).when(mockSecurityContext).isInitialized();

        EtradeRestTemplateFactory mockTemplateFactory = Mockito.mock(EtradeRestTemplateFactory.class);
        Mockito.doReturn(mockSecurityContext).when(mockTemplateFactory).getSecurityContext();

        PositionLotsResponse.PositionLot positionLot1 = new PositionLotsResponse.PositionLot();
        positionLot1.setRemainingQty(10F);
        positionLot1.setTargetPrice(100.00F);
        PositionLotsResponse.PositionLot positionLot2 = new PositionLotsResponse.PositionLot();
        positionLot2.setRemainingQty(10F);
        positionLot2.setTargetPrice(103.00F);

        List<PositionLotsResponse.PositionLot> positionLotList = new ArrayList<>();
        positionLotList.add(positionLot1);
        positionLotList.add(positionLot2);
        EtradeSellOrderCreator.SymbolToLotsIndexPutEvent runnable = Mockito.spy(new EtradeSellOrderCreator
                .SymbolToLotsIndexPutEvent("ABC", positionLotList));
        runnable.setApiConfig(apiConfig);
        runnable.setRestTemplateFactory(mockTemplateFactory);
        Mockito.doAnswer(invocation -> null).when(runnable).setOAuthHeader(Mockito.any(), Mockito.any());

        List<PreviewOrderResponse.PreviewId> previewIdList = new ArrayList<>();
        previewIdList.add(new PreviewOrderResponse.PreviewId());
        PreviewOrderResponse previewOrderResponse = new PreviewOrderResponse();
        previewOrderResponse.setPreviewIdList(previewIdList);
        Mockito.doReturn(previewOrderResponse).when(runnable).fetchPreviewOrderResponse(Mockito.any(), Mockito.any());
        Mockito.doAnswer(invocation -> null).when(runnable)
                .cancelOrder(Mockito.any(), Mockito.anyLong());
        Mockito.doAnswer(invocation -> null).when(runnable)
                .placeOrder(Mockito.any(), Mockito.anyString(), Mockito.any(), Mockito.any());

        runnable.run();
        Mockito.verify(runnable, Mockito.times(1))
                .cancelOrder(Mockito.any(), Mockito.anyLong());
        Mockito.verify(runnable, Mockito.times(2))
                .placeOrder(Mockito.any(), Mockito.anyString(), Mockito.any(), Mockito.any());
    }
}
