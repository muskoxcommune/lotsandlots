package io.lotsandlots.web.servlet;

import io.lotsandlots.etrade.EtradeOrdersDataFetcher;
import io.lotsandlots.etrade.EtradePortfolioDataFetcher;
import io.lotsandlots.etrade.api.PositionLotsResponse;
import io.lotsandlots.etrade.model.Order;
import io.lotsandlots.web.listener.LifecycleListener;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Test(groups = {"unit"})
public class ViewEtradeLotsServletTest {

    public void testDoGet() throws Exception {
        EtradeOrdersDataFetcher mockOrdersFetcher = Mockito.mock(EtradeOrdersDataFetcher.class);
        Map<String, List<Order>> symbolToOrdersIndex = new HashMap<>();
        Mockito.doReturn(symbolToOrdersIndex).when(mockOrdersFetcher).getSymbolToSellOrdersIndex();

        EtradePortfolioDataFetcher mockPortfolioFetcher = Mockito.mock(EtradePortfolioDataFetcher.class);
        Map<String, List<PositionLotsResponse.PositionLot>> symbolToLotsIndex = new HashMap<>();
        Mockito.doReturn(symbolToLotsIndex).when(mockPortfolioFetcher).getSymbolToLotsIndex();

        HttpServletRequest mockRequest = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse mockResponse = Mockito.mock(HttpServletResponse.class);
        PrintWriter mockPrintWriter = Mockito.mock(PrintWriter.class);
        Mockito.doAnswer(invocation -> {
            String html = invocation.getArgument(0);
            Document document = Jsoup.parse(html);
            Element body = document.body();
            Assert.assertEquals(body.tagName(), "body");
            Element table = body.getElementById("lots");
            Assert.assertNotNull(table);

            // TODO
            return null;
        }).when(mockPrintWriter).print(Mockito.anyString());
        Mockito.doReturn(mockPrintWriter).when(mockResponse).getWriter();

        LifecycleListener mockLifecycleListener = Mockito.mock(LifecycleListener.class);
        Mockito.doReturn(mockOrdersFetcher).when(mockLifecycleListener).getOrdersDataFetcher();
        Mockito.doReturn(mockPortfolioFetcher).when(mockLifecycleListener).getPortfolioDataFetcher();

        ViewEtradeLotsServlet viewLotsServlet = new ViewEtradeLotsServlet();
        viewLotsServlet.setLifecycleListener(mockLifecycleListener);

        viewLotsServlet.doGet(mockRequest, mockResponse);
        Mockito.verify(mockPrintWriter).print(Mockito.anyString());
    }

    public void testDoGetWithoutData() throws Exception {
        EtradeOrdersDataFetcher mockOrdersFetcher = Mockito.mock(EtradeOrdersDataFetcher.class);
        Map<String, List<Order>> symbolToOrdersIndex = new HashMap<>();
        Mockito.doReturn(symbolToOrdersIndex).when(mockOrdersFetcher).getSymbolToSellOrdersIndex();

        EtradePortfolioDataFetcher mockPortfolioFetcher = Mockito.mock(EtradePortfolioDataFetcher.class);
        Map<String, List<PositionLotsResponse.PositionLot>> symbolToLotsIndex = new HashMap<>();
        Mockito.doReturn(symbolToLotsIndex).when(mockPortfolioFetcher).getSymbolToLotsIndex();

        HttpServletRequest mockRequest = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse mockResponse = Mockito.mock(HttpServletResponse.class);
        PrintWriter mockPrintWriter = Mockito.mock(PrintWriter.class);
        Mockito.doAnswer(invocation -> {
            String html = invocation.getArgument(0);
            Document document = Jsoup.parse(html);
            Element body = document.body();
            Assert.assertEquals(body.tagName(), "body");
            Element table = body.getElementById("lots");
            Assert.assertNotNull(table);
            Assert.assertEquals(table.tagName(), "table");
            return null;
        }).when(mockPrintWriter).print(Mockito.anyString());
        Mockito.doReturn(mockPrintWriter).when(mockResponse).getWriter();

        LifecycleListener mockLifecycleListener = Mockito.mock(LifecycleListener.class);
        Mockito.doReturn(mockOrdersFetcher).when(mockLifecycleListener).getOrdersDataFetcher();
        Mockito.doReturn(mockPortfolioFetcher).when(mockLifecycleListener).getPortfolioDataFetcher();

        ViewEtradeLotsServlet viewLotsServlet = new ViewEtradeLotsServlet();
        viewLotsServlet.setLifecycleListener(mockLifecycleListener);

        viewLotsServlet.doGet(mockRequest, mockResponse);
        Mockito.verify(mockPrintWriter).print(Mockito.anyString());
    }
}
