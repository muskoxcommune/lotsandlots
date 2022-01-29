package io.lotsandlots.etrade;

import com.google.common.cache.Cache;
import io.lotsandlots.etrade.api.ApiConfig;
import io.lotsandlots.etrade.api.OrderDetail;
import io.lotsandlots.etrade.api.OrdersResponse;
import io.lotsandlots.etrade.model.Order;
import io.lotsandlots.etrade.oauth.SecurityContext;
import io.lotsandlots.etrade.rest.EtradeRestTemplate;
import io.lotsandlots.etrade.rest.EtradeRestTemplateFactory;
import io.lotsandlots.etrade.rest.Message;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Test(groups = {"unit"})
public class EtradeOrdersDataFetcherTest {

    @BeforeClass
    public void beforeClass() throws GeneralSecurityException {
        EtradeRestTemplateFactory.init();
    }

    public void testFetchOrdersResponse() throws GeneralSecurityException, UnsupportedEncodingException {
        Long testTimeMillis = System.currentTimeMillis();
        OrderDetail.Product product1 = new OrderDetail.Product();
        product1.setSymbol("TEST1");
        OrderDetail.Instrument instrument1 = new OrderDetail.Instrument();
        instrument1.setFilledQuantity(0.0F);
        instrument1.setOrderAction("SELL");
        instrument1.setOrderedQuantity(10L);
        instrument1.setProduct(product1);
        List<OrderDetail.Instrument> instrumentList1 = new LinkedList<>();
        instrumentList1.add(instrument1);
        OrderDetail orderDetail1 = new OrderDetail();
        orderDetail1.setInstrumentList(instrumentList1);
        orderDetail1.setLimitPrice(99.99F);
        orderDetail1.setOrderValue(999.90F);
        orderDetail1.setPlacedTime(testTimeMillis);
        orderDetail1.setStatus("OPEN");
        List<OrderDetail> orderDetailList1 = new LinkedList<>();
        orderDetailList1.add(orderDetail1);
        OrdersResponse.Order order1 = new OrdersResponse.Order();
        order1.setOrderDetailList(orderDetailList1);
        order1.setOrderId(1L);
        List<OrdersResponse.Order> testOrderList = new LinkedList<>();
        testOrderList.add(order1);
        OrdersResponse testResponse = new OrdersResponse();
        testResponse.setOrderList(testOrderList);

        ResponseEntity<OrdersResponse> mockResponseEntity = Mockito.mock(ResponseEntity.class);
        Mockito.doReturn(testResponse).when(mockResponseEntity).getBody();
        EtradeRestTemplate mockRestTemplate = Mockito.mock(EtradeRestTemplate.class);
        Mockito.doReturn(mockResponseEntity).when(mockRestTemplate).doGet(Mockito.any(Message.class), Mockito.any());
        EtradeRestTemplateFactory mockTemplateFactory = Mockito.mock(EtradeRestTemplateFactory.class);
        Mockito.doReturn(mockRestTemplate).when(mockTemplateFactory).newCustomRestTemplate();

        SecurityContext securityContext = EtradeRestTemplateFactory.getTemplateFactory().newSecurityContext();
        EtradeOrdersDataFetcher dataFetcher = Mockito.spy(new EtradeOrdersDataFetcher());
        dataFetcher.setRestTemplateFactory(mockTemplateFactory);
        dataFetcher.fetchOrdersResponse(securityContext, null);

        Cache<Long, Order> orderCache = dataFetcher.getOrderCache();
        Order cachedOrder = orderCache.getIfPresent(1L);
        Assert.assertNotNull(cachedOrder);
        Assert.assertEquals(cachedOrder.getLimitPrice(), 99.99F);
        Assert.assertEquals(cachedOrder.getOrderAction(), "SELL");
        Assert.assertEquals(cachedOrder.getOrderValue(), 999.90F);
        Assert.assertEquals(cachedOrder.getOrderedQuantity().longValue(), 10L);
        Assert.assertEquals(cachedOrder.getPlacedTime(), testTimeMillis);
        Assert.assertEquals(cachedOrder.getStatus(), "OPEN");
        Assert.assertEquals(cachedOrder.getSymbol(), "TEST1");
    }

    public void testHandleOrderResponse2L() {
        List<OrdersResponse.Order> testOrderList = new LinkedList<>();
        List<OrderDetail.Instrument> instrumentList1 = new LinkedList<>();
        List<OrderDetail> orderDetailList1 = new LinkedList<>();
        OrdersResponse testResponse = new OrdersResponse();
        OrdersResponse.Order order1 = new OrdersResponse.Order();
        OrderDetail orderDetail1 = new OrderDetail();
        OrderDetail.Instrument instrument1 = new OrderDetail.Instrument();
        OrderDetail.Product product1 = new OrderDetail.Product();

        Long testTimeMillis = System.currentTimeMillis();
        product1.setSymbol("TEST2");
        instrument1.setOrderAction("BUY");
        instrument1.setProduct(product1);
        instrumentList1.add(instrument1);
        orderDetail1.setInstrumentList(instrumentList1);
        orderDetail1.setPlacedTime(testTimeMillis);
        orderDetail1.setStatus("OPEN");
        orderDetailList1.add(orderDetail1);
        order1.setOrderDetailList(orderDetailList1);
        order1.setOrderId(2L);
        testOrderList.add(order1);
        testResponse.setOrderList(testOrderList);

        EtradeOrdersDataFetcher dataFetcher = new EtradeOrdersDataFetcher();
        dataFetcher.handleOrderResponse(testResponse);
        Cache<Long, Order> orderCache = dataFetcher.getOrderCache();
        Order cachedOrder = orderCache.getIfPresent(2L);
        Assert.assertNotNull(cachedOrder);
        Assert.assertEquals(cachedOrder.getSymbol(), "TEST2");
    }

    public void testHandleOrderResponse3L() {
        List<OrdersResponse.Order> testOrderList = new LinkedList<>();
        List<OrderDetail.Instrument> instrumentList1 = new LinkedList<>();
        List<OrderDetail> orderDetailList1 = new LinkedList<>();
        OrdersResponse testResponse = new OrdersResponse();
        OrdersResponse.Order order1 = new OrdersResponse.Order();
        OrderDetail orderDetail1 = new OrderDetail();
        OrderDetail.Instrument instrument1 = new OrderDetail.Instrument();
        OrderDetail.Product product1 = new OrderDetail.Product();

        Long testTimeMillis = System.currentTimeMillis();
        product1.setSymbol("TEST3");
        instrument1.setOrderAction("BUY");
        instrument1.setProduct(product1);
        instrumentList1.add(instrument1);
        orderDetail1.setInstrumentList(instrumentList1);
        orderDetail1.setPlacedTime(testTimeMillis);
        orderDetail1.setStatus("PARTIAL");
        orderDetailList1.add(orderDetail1);
        order1.setOrderDetailList(orderDetailList1);
        order1.setOrderId(3L);
        testOrderList.add(order1);
        testResponse.setOrderList(testOrderList);

        EtradeOrdersDataFetcher dataFetcher = new EtradeOrdersDataFetcher();
        dataFetcher.handleOrderResponse(testResponse);
        Cache<Long, Order> orderCache = dataFetcher.getOrderCache();
        Order cachedOrder = orderCache.getIfPresent(3L);
        Assert.assertNotNull(cachedOrder);
        Assert.assertEquals(cachedOrder.getSymbol(), "TEST3");
    }

    public void testHandleOrderResponse4L() {
        List<OrdersResponse.Order> testOrderList = new LinkedList<>();
        List<OrderDetail.Instrument> instrumentList1 = new LinkedList<>();
        List<OrderDetail> orderDetailList1 = new LinkedList<>();
        OrdersResponse testResponse = new OrdersResponse();
        OrdersResponse.Order order1 = new OrdersResponse.Order();
        OrderDetail orderDetail1 = new OrderDetail();
        OrderDetail.Instrument instrument1 = new OrderDetail.Instrument();
        OrderDetail.Product product1 = new OrderDetail.Product();

        Long testTimeMillis = System.currentTimeMillis();
        product1.setSymbol("TEST4");
        instrument1.setOrderAction("BUY");
        instrument1.setProduct(product1);
        instrumentList1.add(instrument1);
        orderDetail1.setInstrumentList(instrumentList1);
        orderDetail1.setPlacedTime(testTimeMillis);
        orderDetail1.setStatus("EXECUTED");
        orderDetailList1.add(orderDetail1);
        order1.setOrderDetailList(orderDetailList1);
        order1.setOrderId(4L);
        testOrderList.add(order1);
        testResponse.setOrderList(testOrderList);

        EtradeOrdersDataFetcher dataFetcher = new EtradeOrdersDataFetcher();
        dataFetcher.handleOrderResponse(testResponse);
        Cache<Long, Order> orderCache = dataFetcher.getOrderCache();
        Order cachedOrder = orderCache.getIfPresent(4L);
        Assert.assertNull(cachedOrder);
    }

    public void testHandleOrderResponse5L() {
        List<OrdersResponse.Order> testOrderList = new LinkedList<>();
        List<OrderDetail.Instrument> instrumentList1 = new LinkedList<>();
        List<OrderDetail> orderDetailList1 = new LinkedList<>();
        OrdersResponse testResponse = new OrdersResponse();
        OrdersResponse.Order order1 = new OrdersResponse.Order();
        OrderDetail orderDetail1 = new OrderDetail();
        OrderDetail.Instrument instrument1 = new OrderDetail.Instrument();
        OrderDetail.Product product1 = new OrderDetail.Product();

        Long testTimeMillis = System.currentTimeMillis();
        product1.setSymbol("TEST5");
        instrument1.setOrderAction("BUY");
        instrument1.setProduct(product1);
        instrumentList1.add(instrument1);
        orderDetail1.setInstrumentList(instrumentList1);
        orderDetail1.setPlacedTime(testTimeMillis);
        orderDetail1.setStatus("CANCELLED");
        orderDetailList1.add(orderDetail1);
        order1.setOrderDetailList(orderDetailList1);
        order1.setOrderId(5L);
        testOrderList.add(order1);
        testResponse.setOrderList(testOrderList);

        EtradeOrdersDataFetcher dataFetcher = new EtradeOrdersDataFetcher();
        dataFetcher.handleOrderResponse(testResponse);
        Cache<Long, Order> orderCache = dataFetcher.getOrderCache();
        Order cachedOrder = orderCache.getIfPresent(5L);
        Assert.assertNull(cachedOrder);
    }

    public void testHandleOrderResponse6L() {
        List<OrdersResponse.Order> testOrderList = new LinkedList<>();
        List<OrderDetail.Instrument> instrumentList1 = new LinkedList<>();
        List<OrderDetail> orderDetailList1 = new LinkedList<>();
        OrdersResponse testResponse = new OrdersResponse();
        OrdersResponse.Order order1 = new OrdersResponse.Order();
        OrderDetail orderDetail1 = new OrderDetail();
        OrderDetail.Instrument instrument1 = new OrderDetail.Instrument();
        OrderDetail.Product product1 = new OrderDetail.Product();

        Long testTimeMillis = System.currentTimeMillis();
        product1.setSymbol("TEST6");
        instrument1.setOrderAction("BUY");
        instrument1.setProduct(product1);
        instrumentList1.add(instrument1);
        orderDetail1.setInstrumentList(instrumentList1);
        orderDetail1.setPlacedTime(testTimeMillis);
        orderDetail1.setStatus("EXPIRED");
        orderDetailList1.add(orderDetail1);
        order1.setOrderDetailList(orderDetailList1);
        order1.setOrderId(6L);
        testOrderList.add(order1);
        testResponse.setOrderList(testOrderList);

        EtradeOrdersDataFetcher dataFetcher = new EtradeOrdersDataFetcher();
        dataFetcher.handleOrderResponse(testResponse);
        Cache<Long, Order> orderCache = dataFetcher.getOrderCache();
        Order cachedOrder = orderCache.getIfPresent(6L);
        Assert.assertNull(cachedOrder);
    }

    public void testIndexOrdersBySymbol() {
        List<OrdersResponse.Order> testOrderList = new LinkedList<>();
        List<OrderDetail.Instrument> instrumentList1 = new LinkedList<>();
        List<OrderDetail> orderDetailList1 = new LinkedList<>();
        OrdersResponse testResponse = new OrdersResponse();
        OrdersResponse.Order order1 = new OrdersResponse.Order();
        OrdersResponse.Order order2 = new OrdersResponse.Order();
        OrdersResponse.Order order3 = new OrdersResponse.Order();
        OrderDetail orderDetail1 = new OrderDetail();
        OrderDetail.Instrument instrument1 = new OrderDetail.Instrument();
        OrderDetail.Product product1 = new OrderDetail.Product();

        Long testTimeMillis = System.currentTimeMillis();
        product1.setSymbol("TEST7");
        instrument1.setOrderAction("BUY");
        instrument1.setProduct(product1);
        instrumentList1.add(instrument1);
        orderDetail1.setInstrumentList(instrumentList1);
        orderDetail1.setPlacedTime(testTimeMillis);
        orderDetail1.setStatus("OPEN");
        orderDetailList1.add(orderDetail1);
        order1.setOrderDetailList(orderDetailList1);
        order1.setOrderId(7L);
        order2.setOrderDetailList(orderDetailList1);
        order2.setOrderId(8L);
        order3.setOrderDetailList(orderDetailList1);
        order3.setOrderId(9L);
        testOrderList.add(order1);
        testOrderList.add(order2);
        testOrderList.add(order3);
        testResponse.setOrderList(testOrderList);

        EtradeOrdersDataFetcher dataFetcher = new EtradeOrdersDataFetcher();
        dataFetcher.handleOrderResponse(testResponse);
        dataFetcher.indexOrdersBySymbol();
        Map<String, List<Order>> symbolToOrdersIndex =
                EtradeOrdersDataFetcher.getSymbolToBuyOrdersIndex(dataFetcher);
        Assert.assertTrue(symbolToOrdersIndex.containsKey("TEST7"));
        Assert.assertEquals(symbolToOrdersIndex.get("TEST7").size(), 3);
    }

    public void testNewOrdersMessageWithMarker() {
        Message ordersMessage = new EtradeOrdersDataFetcher().newOrdersMessage("test");
        Assert.assertTrue(ordersMessage.getQueryString().contains("fromDate"));
        Assert.assertTrue(ordersMessage.getQueryString().contains("toDate"));
        Assert.assertTrue(ordersMessage.getQueryString().contains("marker=test"));
    }

    public void testNewOrdersMessageWithoutMarker() {
        Message ordersMessage = new EtradeOrdersDataFetcher().newOrdersMessage(null);
        Assert.assertTrue(ordersMessage.getQueryString().contains("fromDate"));
        Assert.assertTrue(ordersMessage.getQueryString().contains("toDate"));
        Assert.assertFalse(ordersMessage.getQueryString().contains("marker"));
    }

    public void testRun() throws GeneralSecurityException, UnsupportedEncodingException {
        SecurityContext mockSecurityContext = Mockito.mock(SecurityContext.class);
        Mockito.doReturn(true).when(mockSecurityContext).isInitialized();
        EtradeRestTemplateFactory mockTemplateFactory = Mockito.mock(EtradeRestTemplateFactory.class);
        Mockito.doReturn(mockSecurityContext).when(mockTemplateFactory).getSecurityContext();

        EtradeOrdersDataFetcher dataFetcher = Mockito.spy(new EtradeOrdersDataFetcher());
        dataFetcher.setRestTemplateFactory(mockTemplateFactory);
        Mockito.doAnswer(invocation -> null).when(dataFetcher)
                .fetchOrdersResponse(Mockito.any(), Mockito.any());
        Mockito.doAnswer(invocation -> null).when(dataFetcher).indexOrdersBySymbol();
        dataFetcher.run();
        Mockito.verify(dataFetcher).fetchOrdersResponse(Mockito.any(SecurityContext.class), ArgumentMatchers.any());
        Mockito.verify(dataFetcher).indexOrdersBySymbol();
    }

    public void testRunWithException() throws GeneralSecurityException, UnsupportedEncodingException {
        SecurityContext mockSecurityContext = Mockito.mock(SecurityContext.class);
        Mockito.doReturn(true).when(mockSecurityContext).isInitialized();
        EtradeRestTemplateFactory mockTemplateFactory = Mockito.mock(EtradeRestTemplateFactory.class);
        Mockito.doReturn(mockSecurityContext).when(mockTemplateFactory).getSecurityContext();

        EtradeOrdersDataFetcher dataFetcher = Mockito.spy(new EtradeOrdersDataFetcher());
        dataFetcher.setRestTemplateFactory(mockTemplateFactory);
        Mockito.doThrow(new RuntimeException("Thrown for test")).when(dataFetcher)
                .fetchOrdersResponse(Mockito.any(), Mockito.any());
        Mockito.doAnswer(invocation -> null).when(dataFetcher).indexOrdersBySymbol();
        dataFetcher.run();
        Mockito.verify(dataFetcher).fetchOrdersResponse(Mockito.any(SecurityContext.class), ArgumentMatchers.any());
        Mockito.verify(dataFetcher, Mockito.times(0))
                .indexOrdersBySymbol();
    }

    public void testRunWithoutOrdersUrl() throws GeneralSecurityException, UnsupportedEncodingException {
        SecurityContext mockSecurityContext = Mockito.mock(SecurityContext.class);
        Mockito.doReturn(true).when(mockSecurityContext).isInitialized();
        EtradeRestTemplateFactory mockTemplateFactory = Mockito.mock(EtradeRestTemplateFactory.class);
        Mockito.doReturn(mockSecurityContext).when(mockTemplateFactory).getSecurityContext();

        ApiConfig mockApiConfig = Mockito.mock(ApiConfig.class);
        EtradeOrdersDataFetcher dataFetcher = Mockito.spy(new EtradeOrdersDataFetcher());
        dataFetcher.setApiConfig(mockApiConfig);
        dataFetcher.setRestTemplateFactory(mockTemplateFactory);
        Mockito.doAnswer(invocation -> null).when(dataFetcher)
                .fetchOrdersResponse(Mockito.any(), Mockito.any());
        Mockito.doAnswer(invocation -> null).when(dataFetcher).indexOrdersBySymbol();
        dataFetcher.run();
        Mockito.verify(dataFetcher).getApiConfig();
        Mockito.verify(mockApiConfig).getOrdersUrl();
        Mockito.verify(dataFetcher, Mockito.times(0))
                .fetchOrdersResponse(Mockito.any(SecurityContext.class), ArgumentMatchers.any());
        Mockito.verify(dataFetcher, Mockito.times(0))
                .indexOrdersBySymbol();
    }

    public void testRunWithUninitializedSecurityContext() throws GeneralSecurityException, UnsupportedEncodingException {
        SecurityContext mockSecurityContext = Mockito.mock(SecurityContext.class);
        Mockito.doReturn(false).when(mockSecurityContext).isInitialized();
        EtradeRestTemplateFactory mockTemplateFactory = Mockito.mock(EtradeRestTemplateFactory.class);
        Mockito.doReturn(mockSecurityContext).when(mockTemplateFactory).getSecurityContext();

        EtradeOrdersDataFetcher dataFetcher = Mockito.spy(new EtradeOrdersDataFetcher());
        dataFetcher.setRestTemplateFactory(mockTemplateFactory);
        Mockito.doAnswer(invocation -> null).when(dataFetcher)
                .fetchOrdersResponse(Mockito.any(), Mockito.any());
        Mockito.doAnswer(invocation -> null).when(dataFetcher).indexOrdersBySymbol();
        dataFetcher.run();
        Mockito.verify(dataFetcher, Mockito.times(0))
                .fetchOrdersResponse(Mockito.any(SecurityContext.class), ArgumentMatchers.any());
        Mockito.verify(dataFetcher, Mockito.times(0))
                .indexOrdersBySymbol();
    }
}
