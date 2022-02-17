package io.lotsandlots.etrade;

import com.google.common.cache.Cache;
import io.lotsandlots.etrade.api.OrderDetail;
import io.lotsandlots.etrade.api.PlaceOrderResponse;
import io.lotsandlots.etrade.api.PreviewOrderRequest;
import io.lotsandlots.etrade.api.PreviewOrderResponse;
import io.lotsandlots.etrade.model.Order;
import io.lotsandlots.etrade.oauth.SecurityContext;
import io.lotsandlots.etrade.rest.EtradeRestTemplate;
import io.lotsandlots.etrade.rest.EtradeRestTemplateFactory;
import io.lotsandlots.etrade.rest.Message;
import io.lotsandlots.web.listener.LifecycleListener;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.security.GeneralSecurityException;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

@Test(groups = {"unit"})
public class EtradeOrderCreatorTest {

    static class TestEtradeOrderCreator extends EtradeOrderCreator {

        @Override
        public void run() {
        }
    }

    @BeforeClass
    public void beforeClass() throws GeneralSecurityException {
        EtradeRestTemplateFactory.init();
    }

    public void testNewPreviewOrderRequest() throws Exception {
        String clientOrderId = UUID.randomUUID().toString().substring(0, 8);;
        float limitPrice = (float) Math.random();

        TestEtradeOrderCreator etradeOrderCreator = new TestEtradeOrderCreator();
        OrderDetail orderDetail = new OrderDetail();
        orderDetail.setLimitPrice(limitPrice);
        PreviewOrderRequest previewOrderRequest = etradeOrderCreator.newPreviewOrderRequest(clientOrderId, orderDetail);
        Assert.assertEquals(clientOrderId, previewOrderRequest.getClientOrderId());
        Assert.assertEquals(previewOrderRequest.getOrderType(), "EQ");
        Assert.assertEquals(limitPrice, previewOrderRequest.getOrderDetailList().get(0).getLimitPrice());
    }

    public void testPlaceOrder() throws Exception {
        EtradeRestTemplate mockRestTemplate = Mockito.mock(EtradeRestTemplate.class);
        Mockito.when(mockRestTemplate.doPost(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> {
                    PreviewOrderResponse.PreviewId previewId = new PreviewOrderResponse.PreviewId();
                    previewId.setPreviewId(998L);

                    List<PreviewOrderResponse.PreviewId> previewIdList = new LinkedList<>();
                    previewIdList.add(previewId);

                    PreviewOrderResponse previewOrderResponse = new PreviewOrderResponse();
                    previewOrderResponse.setPreviewIdList(previewIdList);

                    ResponseEntity<PreviewOrderResponse> mockResponseEntity = Mockito.mock(ResponseEntity.class);
                    Mockito.doReturn(previewOrderResponse).when(mockResponseEntity).getBody();
                    return mockResponseEntity;
                })
                .thenAnswer(invocation -> {
                    OrderDetail.Instrument instrument = new OrderDetail.Instrument();
                    List<OrderDetail.Instrument> instrumentList = new LinkedList<>();
                    instrumentList.add(instrument);

                    OrderDetail orderDetail = new OrderDetail();
                    orderDetail.setInstrumentList(instrumentList);

                    List<OrderDetail> orderDetailList = new LinkedList<>();
                    orderDetailList.add(orderDetail);

                    PlaceOrderResponse.OrderId orderId = new PlaceOrderResponse.OrderId();
                    orderId.setOrderId(999L);

                    List<PlaceOrderResponse.OrderId> orderIdList = new LinkedList<>();
                    orderIdList.add(orderId);

                    PlaceOrderResponse placeOrderResponse = new PlaceOrderResponse();
                    placeOrderResponse.setOrderDetailList(orderDetailList);
                    placeOrderResponse.setOrderIdList(orderIdList);

                    ResponseEntity<PlaceOrderResponse> mockResponseEntity = Mockito.mock(ResponseEntity.class);
                    Mockito.doReturn(placeOrderResponse).when(mockResponseEntity).getBody();
                    return mockResponseEntity;
                });

        EtradeRestTemplateFactory mockRestTemplateFactory = Mockito.mock(EtradeRestTemplateFactory.class);
        Mockito.doReturn(mockRestTemplate).when(mockRestTemplateFactory).newCustomRestTemplate();

        OrderDetail.Product product = new OrderDetail.Product();
        product.setSymbol("TEST_PLACE_ORDER");

        OrderDetail.Instrument instrument = new OrderDetail.Instrument();
        instrument.setProduct(product);

        List<OrderDetail.Instrument> instrumentList = new LinkedList<>();
        instrumentList.add(instrument);

        float limitPrice = (float) Math.random();
        OrderDetail orderDetail = new OrderDetail();
        orderDetail.setLimitPrice(limitPrice);
        orderDetail.setInstrumentList(instrumentList);

        Cache<Long, Order> mockOrderCache = Mockito.mock(Cache.class);
        EtradeOrdersDataFetcher mockDataFetcher = Mockito.mock(EtradeOrdersDataFetcher.class);
        Mockito.doReturn(mockOrderCache).when(mockDataFetcher).getOrderCache();

        LifecycleListener mockLifecycleListener = Mockito.mock(LifecycleListener.class);
        Mockito.doReturn(mockDataFetcher).when(mockLifecycleListener).getEtradeOrdersDataFetcher();

        TestEtradeOrderCreator etradeOrderCreator = Mockito.spy(new TestEtradeOrderCreator());
        Mockito.doAnswer(invocation -> {
            Message message = invocation.getArgument(1, Message.class);
            Assert.assertEquals(message.getHttpMethod(), "POST");
            return null;
        }).when(etradeOrderCreator).setOAuthHeader(Mockito.any(SecurityContext.class), Mockito.any(Message.class));
        etradeOrderCreator.setLifecycleListener(mockLifecycleListener);
        etradeOrderCreator.setRestTemplateFactory(mockRestTemplateFactory);

        String clientOrderId = UUID.randomUUID().toString().substring(0, 8);;
        SecurityContext mockSecurityContext = Mockito.mock(SecurityContext.class);
        Order placedOrder = etradeOrderCreator.placeOrder(mockSecurityContext, clientOrderId, orderDetail);
        Mockito.verify(mockRestTemplate, Mockito.times(2)).doPost(Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(mockOrderCache).put(Mockito.anyLong(), Mockito.any(Order.class));
        Mockito.verify(mockDataFetcher).indexOrdersBySymbol();
    }
}
