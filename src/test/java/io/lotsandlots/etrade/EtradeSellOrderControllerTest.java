package io.lotsandlots.etrade;

import io.lotsandlots.etrade.api.*;
import io.lotsandlots.etrade.model.Order;
import io.lotsandlots.etrade.oauth.SecurityContext;
import io.lotsandlots.etrade.rest.EtradeRestTemplate;
import io.lotsandlots.etrade.rest.EtradeRestTemplateFactory;
import io.lotsandlots.etrade.rest.Message;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
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
public class EtradeSellOrderControllerTest {

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

        EtradeSellOrderController.SymbolToLotsIndexPutEventRunnable runnable =
                Mockito.spy(new EtradeSellOrderController()
                        .newSymbolToLotsIndexPutEventRunnable("CANCEL_ORDER", new ArrayList<>()));
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

        EtradeSellOrderController.SymbolToLotsIndexPutEventRunnable runnable =
                Mockito.spy(new EtradeSellOrderController()
                        .newSymbolToLotsIndexPutEventRunnable("FETCH_PREVIEW_ORDER_RESPONSE", new ArrayList<>()));
        runnable.setRestTemplateFactory(mockTemplateFactory);
        Mockito.doAnswer(invocation -> null).when(runnable).setOAuthHeader(Mockito.any(), Mockito.any());

        PreviewOrderRequest previewOrderRequest = new PreviewOrderRequest();
        SecurityContext mockSecurityContext = Mockito.mock(SecurityContext.class);
        runnable.fetchPreviewOrderResponse(mockSecurityContext, previewOrderRequest);
        Mockito.verify(mockRestTemplate).doPost(Mockito.any(), Mockito.any(), Mockito.any());
    }

    public void testHandleSymbolToLotsIndexPut() {

        EtradeOrdersDataFetcher mockOrdersDataFetcher;
        EtradeSellOrderController sellOrderController;
        ExecutorService mockExecutor;

        ////
        // If selling is disabled for a symbol, we should not submit a SymbolToLotsIndexPutEventRunnable.

        sellOrderController = Mockito.spy(new EtradeSellOrderController());
        Mockito.doReturn(true).when(sellOrderController).isSellOrderCreationDisabled(Mockito.anyString());

        mockExecutor = Mockito.mock(ExecutorService.class);
        sellOrderController.setExecutor(mockExecutor);
        sellOrderController.handleSymbolToLotsIndexPut(
                "SYMBOL_TO_LOT_INDEX_PUT_WITH_DISABLED_SYMBOL", new ArrayList<>(), new PortfolioResponse.Totals());
        Mockito.verify(mockExecutor, Mockito.times(0))
                .submit(Mockito.any(EtradeSellOrderController.SymbolToLotsIndexPutEventRunnable.class));

        ////
        // If orders data fetching has not been completed yet, we should not submit a SymbolToLotsIndexPutEventRunnable.

        sellOrderController = Mockito.spy(new EtradeSellOrderController());

        mockOrdersDataFetcher = Mockito.mock(EtradeOrdersDataFetcher.class);
        Mockito.doReturn(null).when(mockOrdersDataFetcher).getLastSuccessfulFetchTimeMillis();
        sellOrderController.setOrdersDataFetcher(mockOrdersDataFetcher);

        mockExecutor = Mockito.mock(ExecutorService.class);
        sellOrderController.setExecutor(mockExecutor);
        sellOrderController.handleSymbolToLotsIndexPut(
                "SYMBOL_TO_LOT_INDEX_PUT_BEFORE_ORDER_FETCH_COMPLETION", new ArrayList<>(), new PortfolioResponse.Totals());
        Mockito.verify(mockExecutor, Mockito.times(0))
                .submit(Mockito.any(EtradeSellOrderController.SymbolToLotsIndexPutEventRunnable.class));

        ////
        // If orders data is not stale, we should submit a SymbolToLotsIndexPutEventRunnable.

        mockOrdersDataFetcher = Mockito.mock(EtradeOrdersDataFetcher.class);
        Mockito.doReturn(System.currentTimeMillis()).when(mockOrdersDataFetcher).getLastSuccessfulFetchTimeMillis();
        Mockito.doReturn(120L).when(mockOrdersDataFetcher).getOrdersDataExpirationSeconds();
        sellOrderController.setOrdersDataFetcher(mockOrdersDataFetcher);

        mockExecutor = Mockito.mock(ExecutorService.class);
        sellOrderController.setExecutor(mockExecutor);
        sellOrderController.handleSymbolToLotsIndexPut(
                "SYMBOL_TO_LOT_INDEX_PUT_WITH_FRESH_ORDERS_DATA", new ArrayList<>(), new PortfolioResponse.Totals());
        Mockito.verify(mockExecutor).submit(Mockito.any(EtradeSellOrderController.SymbolToLotsIndexPutEventRunnable.class));

        ////
        // If orders data is stale, we should not submit a SymbolToLotsIndexPutEventRunnable.
        mockOrdersDataFetcher = Mockito.mock(EtradeOrdersDataFetcher.class);
        Mockito.doReturn(System.currentTimeMillis() - 2000L).when(mockOrdersDataFetcher).getLastSuccessfulFetchTimeMillis();
        Mockito.doReturn(1L).when(mockOrdersDataFetcher).getOrdersDataExpirationSeconds();
        sellOrderController.setOrdersDataFetcher(mockOrdersDataFetcher);

        mockExecutor = Mockito.mock(ExecutorService.class);
        sellOrderController.setExecutor(mockExecutor);
        sellOrderController.handleSymbolToLotsIndexPut(
                "SYMBOL_TO_LOT_INDEX_PUT_WITH_STALE_ORDERS_DATA", new ArrayList<>(), new PortfolioResponse.Totals());
        Mockito.verify(mockExecutor, Mockito.times(0))
                .submit(Mockito.any(EtradeSellOrderController.SymbolToLotsIndexPutEventRunnable.class));
    }

    public void testSymbolToLotsIndexPutEventRunnableRun() throws Exception {
        Order order1 = new Order();
        order1.setOrderId(1L);
        List<Order> orderList = new ArrayList<>();
        orderList.add(order1);
        Map<String, List<Order>> symbolToOrdersIndex = Mockito.spy(new HashMap<>());
        symbolToOrdersIndex.put("SYMBOL_TO_LOT_INDEX_PUT_RUNNABLE_RUN", orderList);

        EtradeOrdersDataFetcher dataFetcher = Mockito.spy(new EtradeOrdersDataFetcher());
        dataFetcher.setSymbolToSellOrdersIndex(symbolToOrdersIndex);

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

        EtradeSellOrderController sellOrderController = new EtradeSellOrderController();
        sellOrderController.setOrdersDataFetcher(dataFetcher);

        EtradeSellOrderController.SymbolToLotsIndexPutEventRunnable runnable =
                Mockito.spy(sellOrderController.newSymbolToLotsIndexPutEventRunnable(
                        "SYMBOL_TO_LOT_INDEX_PUT_RUNNABLE_RUN", positionLotList));
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
        Mockito.doAnswer((Answer<Order>) invocation -> {
            Order mockBuyOrder = new Order();
            mockBuyOrder.setOrderId(123L);
            return mockBuyOrder;
        }).when(runnable).placeOrder(Mockito.any(), Mockito.anyString(), Mockito.any());

        runnable.run();
        Mockito.verify(runnable, Mockito.times(1))
                .cancelOrder(Mockito.any(), Mockito.anyLong());
        Mockito.verify(runnable, Mockito.times(2))
                .placeOrder(Mockito.any(), Mockito.anyString(), Mockito.any());
    }
}
