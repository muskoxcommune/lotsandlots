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
public class EtradeSellOrderControllerTest {

    @BeforeClass
    public void beforeClass() throws GeneralSecurityException {
        EtradeRestTemplateFactory.init();
    }

    public void testCancelOrder() throws Exception {
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

        EtradeSellOrderController.OnPositionLotsUpdateRunnable runnable =
                Mockito.spy(new EtradeSellOrderController(
                            Mockito.mock(EtradePortfolioDataFetcher.class), Mockito.mock(EtradeOrdersDataFetcher.class)
                ).newSymbolToLotsIndexPutEventRunnable("FETCH_PREVIEW_ORDER_RESPONSE"));
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


        mockExecutor = Mockito.mock(ExecutorService.class);
        sellOrderController = Mockito.spy(new EtradeSellOrderController(
                Mockito.mock(EtradePortfolioDataFetcher.class),
                Mockito.mock(EtradeOrdersDataFetcher.class),
                mockExecutor));
        Mockito.doReturn(true).when(sellOrderController).isSellOrderCreationDisabled(Mockito.anyString());
        sellOrderController.handlePositionLotsUpdate(
                "SYMBOL_TO_LOT_INDEX_PUT_WITH_DISABLED_SYMBOL", new PortfolioResponse.Totals());
        Mockito.verify(mockExecutor, Mockito.times(0))
                .submit(Mockito.any(EtradeSellOrderController.OnPositionLotsUpdateRunnable.class));

        ////
        // If orders data fetching has not been completed yet, we should not submit a SymbolToLotsIndexPutEventRunnable.


        mockExecutor = Mockito.mock(ExecutorService.class);
        mockOrdersDataFetcher = Mockito.mock(EtradeOrdersDataFetcher.class);
        Mockito.doReturn(null).when(mockOrdersDataFetcher).getLastSuccessfulFetchTimeMillis();

        sellOrderController = Mockito.spy(new EtradeSellOrderController(
                Mockito.mock(EtradePortfolioDataFetcher.class), mockOrdersDataFetcher, mockExecutor));
        sellOrderController.handlePositionLotsUpdate(
                "SYMBOL_TO_LOT_INDEX_PUT_BEFORE_ORDER_FETCH_COMPLETION", new PortfolioResponse.Totals());
        Mockito.verify(mockExecutor, Mockito.times(0))
                .submit(Mockito.any(EtradeSellOrderController.OnPositionLotsUpdateRunnable.class));

        ////
        // If orders data is not stale, we should submit a SymbolToLotsIndexPutEventRunnable.

        mockExecutor = Mockito.mock(ExecutorService.class);
        mockOrdersDataFetcher = Mockito.mock(EtradeOrdersDataFetcher.class);
        Mockito.doReturn(System.currentTimeMillis()).when(mockOrdersDataFetcher).getLastSuccessfulFetchTimeMillis();
        Mockito.doReturn(120L).when(mockOrdersDataFetcher).getOrdersDataExpirationSeconds();

        sellOrderController = Mockito.spy(new EtradeSellOrderController(
                Mockito.mock(EtradePortfolioDataFetcher.class), mockOrdersDataFetcher, mockExecutor));
        sellOrderController.handlePositionLotsUpdate(
                "SYMBOL_TO_LOT_INDEX_PUT_WITH_FRESH_ORDERS_DATA", new PortfolioResponse.Totals());
        Mockito.verify(mockExecutor).submit(Mockito.any(EtradeSellOrderController.OnPositionLotsUpdateRunnable.class));

        ////
        // If orders data is stale, we should not submit a SymbolToLotsIndexPutEventRunnable.

        mockExecutor = Mockito.mock(ExecutorService.class);
        mockOrdersDataFetcher = Mockito.mock(EtradeOrdersDataFetcher.class);
        Mockito.doReturn(System.currentTimeMillis() - 2000L).when(mockOrdersDataFetcher).getLastSuccessfulFetchTimeMillis();
        Mockito.doReturn(1L).when(mockOrdersDataFetcher).getOrdersDataExpirationSeconds();

        sellOrderController = Mockito.spy(new EtradeSellOrderController(
                Mockito.mock(EtradePortfolioDataFetcher.class), mockOrdersDataFetcher, mockExecutor));
        sellOrderController.handlePositionLotsUpdate(
                "SYMBOL_TO_LOT_INDEX_PUT_WITH_STALE_ORDERS_DATA", new PortfolioResponse.Totals());
    }

    public void testSymbolToLotsIndexPutEventRunnableRun() throws Exception {
        Order order1 = new Order();
        order1.setOrderId(1L);
        List<Order> orderList = new ArrayList<>();
        orderList.add(order1);
        Map<String, List<Order>> symbolToOrdersIndex = Mockito.spy(new HashMap<>());
        symbolToOrdersIndex.put("SYMBOL_TO_LOT_INDEX_PUT_RUNNABLE_RUN", orderList);

        EtradeOrdersDataFetcher ordersDataFetcher = Mockito.spy(new EtradeOrdersDataFetcher());
        ordersDataFetcher.setSymbolToSellOrdersIndex(symbolToOrdersIndex);

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

        EtradeSellOrderController sellOrderController = new EtradeSellOrderController(
                Mockito.mock(EtradePortfolioDataFetcher.class), ordersDataFetcher);

        EtradeSellOrderController.OnPositionLotsUpdateRunnable runnable =
                Mockito.spy(sellOrderController.newSymbolToLotsIndexPutEventRunnable(
                        "SYMBOL_TO_LOT_INDEX_PUT_RUNNABLE_RUN"));
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

        runnable.run();
    }
}
