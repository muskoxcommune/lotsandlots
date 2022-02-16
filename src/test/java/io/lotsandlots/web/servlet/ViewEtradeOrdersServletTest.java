package io.lotsandlots.web.servlet;

import io.lotsandlots.etrade.EtradeOrdersDataFetcher;
import io.lotsandlots.etrade.model.Order;
import io.lotsandlots.web.listener.LifecycleListener;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.testng.Assert;
import org.testng.annotations.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Test(groups = {"unit"})
public class ViewEtradeOrdersServletTest {

    public void testDoGet() throws Exception {
        EtradeOrdersDataFetcher mockOrdersFetcher = Mockito.mock(EtradeOrdersDataFetcher.class);

        Map<String, List<Order>> symbolToBuyOrdersIndex = new HashMap<>();
        List<Order> buyOrderList = new LinkedList<>();
        Order buyOrder = new Order();
        buyOrder.setLimitPrice(1.00F);
        buyOrder.setPlacedTime(System.currentTimeMillis());
        buyOrder.setOrderAction("BUY");
        buyOrder.setSymbol("VIEW_ORDERS_BUY_ORDER");
        buyOrderList.add(buyOrder);
        symbolToBuyOrdersIndex.put("VIEW_ORDERS_BUY_ORDER", buyOrderList);

        Map<String, List<Order>> symbolToSellOrdersIndex = new HashMap<>();
        List<Order> sellOrderList = new LinkedList<>();
        Order sellOrder = new Order();
        sellOrder.setLimitPrice(2.00F);
        sellOrder.setOrderAction("SELL");
        sellOrder.setPlacedTime(System.currentTimeMillis());
        sellOrder.setSymbol("VIEW_ORDERS_SELL_ORDER");
        sellOrderList.add(sellOrder);
        symbolToSellOrdersIndex.put("VIEW_ORDERS_SELL_ORDER", sellOrderList);

        Mockito.doReturn(symbolToBuyOrdersIndex).when(mockOrdersFetcher).getSymbolToBuyOrdersIndex();
        Mockito.doReturn(symbolToSellOrdersIndex).when(mockOrdersFetcher).getSymbolToSellOrdersIndex();

        HttpServletRequest mockRequest = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse mockResponse = Mockito.mock(HttpServletResponse.class);
        PrintWriter mockPrintWriter = Mockito.mock(PrintWriter.class);
        Mockito.doAnswer(invocation -> {
            String html = invocation.getArgument(0);
            Document document = Jsoup.parse(html);
            Element body = document.body();
            Assert.assertEquals(body.tagName(), "body");
            Element table = body.getElementById("orders");
            Assert.assertNotNull(table);
            Assert.assertEquals(table.tagName(), "table");
            Element thead = table.child(0);
            Assert.assertNotNull(thead);
            Assert.assertEquals(thead.tagName(), "thead");
            Element tbody = table.child(1);
            Assert.assertNotNull(tbody);

            Element tr1 = tbody.child(0);
            Assert.assertNotNull(tr1);
            Element tr1td4 = tr1.child(4);
            Assert.assertNotNull(tr1td4);
            Assert.assertEquals(tr1td4.text(), "BUY");

            Element tr2 = tbody.child(1);
            Assert.assertNotNull(tr2);
            Element tr2td4 = tr2.child(4);
            Assert.assertNotNull(tr2td4);
            Assert.assertEquals(tr2td4.text(), "SELL");
            return null;
        }).when(mockPrintWriter).print(Mockito.anyString());
        Mockito.doReturn(mockPrintWriter).when(mockResponse).getWriter();

        LifecycleListener mockLifecycleListener = Mockito.mock(LifecycleListener.class);
        Mockito.doReturn(mockOrdersFetcher).when(mockLifecycleListener).getOrdersDataFetcher();

        ViewEtradeOrdersServlet viewOrdersServlet = new ViewEtradeOrdersServlet();
        viewOrdersServlet.setLifecycleListener(mockLifecycleListener);

        viewOrdersServlet.doGet(mockRequest, mockResponse);
        Mockito.verify(mockPrintWriter).print(Mockito.anyString());
    }

    public void testDoGetWithSymbolParameter() throws Exception {
        EtradeOrdersDataFetcher mockOrdersFetcher = Mockito.mock(EtradeOrdersDataFetcher.class);

        Map<String, List<Order>> symbolToBuyOrdersIndex = new HashMap<>();
        List<Order> buyOrderListA = new LinkedList<>();
        Order buyOrderA = new Order();
        buyOrderA.setLimitPrice(1.00F);
        buyOrderA.setPlacedTime(System.currentTimeMillis());
        buyOrderA.setOrderAction("BUY");
        buyOrderA.setSymbol("VIEW_ORDERS_SYMBOL_A");
        buyOrderListA.add(buyOrderA);
        symbolToBuyOrdersIndex.put("VIEW_ORDERS_SYMBOL_A", buyOrderListA);
        Order buyOrderB = new Order();
        buyOrderB.setLimitPrice(2.00F);
        buyOrderB.setPlacedTime(System.currentTimeMillis());
        buyOrderB.setOrderAction("BUY");
        buyOrderB.setSymbol("VIEW_ORDERS_SYMBOL_B");
        List<Order> buyOrderListB = new LinkedList<>();
        buyOrderListB.add(buyOrderB);
        symbolToBuyOrdersIndex.put("VIEW_ORDERS_SYMBOL_B", buyOrderListB);

        Map<String, List<Order>> symbolToSellOrdersIndex = new HashMap<>();
        List<Order> sellOrderListA = new LinkedList<>();
        Order sellOrderA = new Order();
        sellOrderA.setLimitPrice(3.00F);
        sellOrderA.setOrderAction("SELL");
        sellOrderA.setPlacedTime(System.currentTimeMillis());
        sellOrderA.setSymbol("VIEW_ORDERS_SYMBOL_A");
        sellOrderListA.add(sellOrderA);
        symbolToSellOrdersIndex.put("VIEW_ORDERS_SYMBOL_A", sellOrderListA);
        List<Order> sellOrderListB = new LinkedList<>();
        Order sellOrderB = new Order();
        sellOrderB.setLimitPrice(4.00F);
        sellOrderB.setOrderAction("SELL");
        sellOrderB.setPlacedTime(System.currentTimeMillis());
        sellOrderB.setSymbol("VIEW_ORDERS_SYMBOL_B");
        sellOrderListB.add(sellOrderB);
        symbolToSellOrdersIndex.put("VIEW_ORDERS_SYMBOL_B", sellOrderListB);

        Mockito.doReturn(symbolToBuyOrdersIndex).when(mockOrdersFetcher).getSymbolToBuyOrdersIndex();
        Mockito.doReturn(symbolToSellOrdersIndex).when(mockOrdersFetcher).getSymbolToSellOrdersIndex();

        ////
        // If asked for SYMBOL B, we should not include anything else.

        HttpServletRequest mockRequest = Mockito.mock(HttpServletRequest.class);
        Mockito.doAnswer((Answer<String>) invocation -> {
            String parameter = invocation.getArgument(0);
            if (parameter.equals("symbol")) {
                return "VIEW_ORDERS_SYMBOL_B";
            }
            return null;
        }).when(mockRequest).getParameter(Mockito.anyString());

        HttpServletResponse mockResponse = Mockito.mock(HttpServletResponse.class);
        PrintWriter mockPrintWriter = Mockito.mock(PrintWriter.class);
        Mockito.doAnswer(invocation -> {
            String html = invocation.getArgument(0);
            Document document = Jsoup.parse(html);
            Element body = document.body();
            Assert.assertEquals(body.tagName(), "body");
            Element table = body.getElementById("orders");
            Assert.assertNotNull(table);
            Assert.assertEquals(table.tagName(), "table");
            Element thead = table.child(0);
            Assert.assertNotNull(thead);
            Assert.assertEquals(thead.tagName(), "thead");
            Element tbody = table.child(1);
            Assert.assertNotNull(tbody);
            Elements rows = tbody.children();
            Assert.assertEquals(rows.size(), 2);

            Element tr1 = tbody.child(0);
            Assert.assertNotNull(tr1);
            Element tr1td2 = tr1.child(1);
            Assert.assertNotNull(tr1td2);
            Assert.assertEquals(tr1td2.text(), "VIEW_ORDERS_SYMBOL_B");
            Element tr1td3 = tr1.child(3);
            Assert.assertNotNull(tr1td3);
            Assert.assertEquals(tr1td3.text(), "$2.00");
            Element tr1td4 = tr1.child(4);
            Assert.assertNotNull(tr1td4);
            Assert.assertEquals(tr1td4.text(), "BUY");

            Element tr2 = tbody.child(1);
            Assert.assertNotNull(tr2);
            Element tr2td2 = tr2.child(1);
            Assert.assertNotNull(tr2td2);
            Assert.assertEquals(tr2td2.text(), "VIEW_ORDERS_SYMBOL_B");
            Element tr2td3 = tr2.child(3);
            Assert.assertNotNull(tr2td3);
            Assert.assertEquals(tr2td3.text(), "$4.00");
            Element tr2td4 = tr2.child(4);
            Assert.assertNotNull(tr2td4);
            Assert.assertEquals(tr2td4.text(), "SELL");
            return null;
        }).when(mockPrintWriter).print(Mockito.anyString());
        Mockito.doReturn(mockPrintWriter).when(mockResponse).getWriter();

        LifecycleListener mockLifecycleListener = Mockito.mock(LifecycleListener.class);
        Mockito.doReturn(mockOrdersFetcher).when(mockLifecycleListener).getOrdersDataFetcher();

        ViewEtradeOrdersServlet viewOrdersServlet = new ViewEtradeOrdersServlet();
        viewOrdersServlet.setLifecycleListener(mockLifecycleListener);

        viewOrdersServlet.doGet(mockRequest, mockResponse);
        Mockito.verify(mockPrintWriter).print(Mockito.anyString());
    }

    public void testDoGetWithoutData() throws Exception {
        EtradeOrdersDataFetcher mockOrdersFetcher = Mockito.mock(EtradeOrdersDataFetcher.class);
        Map<String, List<Order>> symbolToBuyOrdersIndex = new HashMap<>();
        Map<String, List<Order>> symbolToSellOrdersIndex = new HashMap<>();
        Mockito.doReturn(symbolToBuyOrdersIndex).when(mockOrdersFetcher).getSymbolToBuyOrdersIndex();
        Mockito.doReturn(symbolToSellOrdersIndex).when(mockOrdersFetcher).getSymbolToSellOrdersIndex();

        HttpServletRequest mockRequest = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse mockResponse = Mockito.mock(HttpServletResponse.class);
        PrintWriter mockPrintWriter = Mockito.mock(PrintWriter.class);
        Mockito.doAnswer(invocation -> {
            String html = invocation.getArgument(0);
            Document document = Jsoup.parse(html);
            Element body = document.body();
            Assert.assertEquals(body.tagName(), "body");
            Element table = body.getElementById("orders");
            Assert.assertNotNull(table);
            Assert.assertEquals(table.tagName(), "table");
            Element thead = table.child(0);
            Assert.assertNotNull(thead);
            Assert.assertEquals(thead.tagName(), "thead");
            Element tbody = table.child(1);
            Assert.assertNotNull(tbody);
            Elements rows = tbody.children();
            Assert.assertEquals(rows.size(), 0);
            return null;
        }).when(mockPrintWriter).print(Mockito.anyString());
        Mockito.doReturn(mockPrintWriter).when(mockResponse).getWriter();

        LifecycleListener mockLifecycleListener = Mockito.mock(LifecycleListener.class);
        Mockito.doReturn(mockOrdersFetcher).when(mockLifecycleListener).getOrdersDataFetcher();

        ViewEtradeOrdersServlet viewOrdersServlet = new ViewEtradeOrdersServlet();
        viewOrdersServlet.setLifecycleListener(mockLifecycleListener);

        viewOrdersServlet.doGet(mockRequest, mockResponse);
        Mockito.verify(mockPrintWriter).print(Mockito.anyString());
    }
}
