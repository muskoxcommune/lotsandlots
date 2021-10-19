package io.lotsandlots;

import com.google.common.cache.Cache;
import io.lotsandlots.etrade.EtradeOrdersDataFetcher;
import io.lotsandlots.etrade.EtradeRestTemplateFactory;
import io.lotsandlots.etrade.api.OrdersResponse;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.security.GeneralSecurityException;
import java.util.LinkedList;
import java.util.List;

@Test(groups = {"unit"})
public class EtradeOrdersDataFetcherTest {

    public void testHandleOrderResponse() throws GeneralSecurityException {
        EtradeRestTemplateFactory.init();

        List<OrdersResponse.Order> testOrderList = new LinkedList<>();
        List<OrdersResponse.OrderDetail.Instrument> instrumentList1 = new LinkedList<>();
        List<OrdersResponse.OrderDetail> orderDetailList1 = new LinkedList<>();
        OrdersResponse testResponse = new OrdersResponse();
        OrdersResponse.Order order1 = new OrdersResponse.Order();
        OrdersResponse.OrderDetail orderDetail1 = new OrdersResponse.OrderDetail();
        OrdersResponse.OrderDetail.Instrument instrument1 = new OrdersResponse.OrderDetail.Instrument();
        OrdersResponse.OrderDetail.Instrument.Product product1 = new OrdersResponse.OrderDetail.Instrument.Product();

        Long testTimeMillis = System.currentTimeMillis();
        product1.setSymbol("TEST1");
        instrument1.setFilledQuantity(0.0F);
        instrument1.setOrderAction("SELL");
        instrument1.setOrderedQuantity(10L);
        instrument1.setProduct(product1);
        instrumentList1.add(instrument1);
        orderDetail1.setInstrumentList(instrumentList1);
        orderDetail1.setLimitPrice(99.99F);
        orderDetail1.setOrderValue(999.90F);
        orderDetail1.setPlacedTime(testTimeMillis);
        orderDetail1.setStatus("OPEN");
        orderDetailList1.add(orderDetail1);
        order1.setOrderDetailList(orderDetailList1);
        order1.setOrderId(1L);
        testOrderList.add(order1);
        testResponse.setOrderList(testOrderList);

        EtradeOrdersDataFetcher dataFetcher = new EtradeOrdersDataFetcher();
        dataFetcher.handleOrderResponse(testResponse);
        Cache<Long, OrdersResponse.Order> orderCache = dataFetcher.getOrderCache();
        OrdersResponse.Order cachedOrder = orderCache.getIfPresent(1L);
        Assert.assertNotNull(cachedOrder);
        Assert.assertEquals(cachedOrder.getFilledQuantity(), 0.0F);
        Assert.assertEquals(cachedOrder.getLimitPrice(), 99.99F);
        Assert.assertEquals(cachedOrder.getOrderAction(), "SELL");
        Assert.assertEquals(cachedOrder.getOrderValue(), 999.90F);
        Assert.assertEquals(cachedOrder.getOrderedQuantity().longValue(), 10L);
        Assert.assertEquals(cachedOrder.getStatus(), "OPEN");
        Assert.assertEquals(cachedOrder.getSymbol(), "TEST1");
    }
}
