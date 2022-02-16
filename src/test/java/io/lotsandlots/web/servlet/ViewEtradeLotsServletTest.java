package io.lotsandlots.web.servlet;

import io.lotsandlots.etrade.EtradeOrdersDataFetcher;
import io.lotsandlots.etrade.EtradePortfolioDataFetcher;
import io.lotsandlots.etrade.api.PositionLotsResponse;
import io.lotsandlots.etrade.model.Order;
import io.lotsandlots.web.listener.LifecycleListener;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.mockito.Mockito;
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
public class ViewEtradeLotsServletTest {

    private void checkColor(Element tr, String getTotalCostForGainPct) {
        switch (getTotalCostForGainPct) {
            case "4.00%":
                Assert.assertTrue(tr.attr("style").endsWith("MediumSeaGreen"));
                break;
            case "1.00%":
                Assert.assertTrue(tr.attr("style").endsWith("PaleGreen"));
                break;
            case "-1.00%":
                Assert.assertTrue(tr.attr("style").endsWith("Pink"));
                break;
            case "-4.00%":
                Assert.assertTrue(tr.attr("style").endsWith("LightCoral"));
                break;
        }
    }

    private void checkStatus(Element statusTd, Element symbolTd) {
        Element p;
        switch(symbolTd.text()) {
            case "VIEW_LOTS_SYMBOL_A":
                p = statusTd.child(0);
                Assert.assertNotNull(p);
                Assert.assertTrue(p.attr("style").endsWith("Red"));
                Assert.assertEquals(statusTd.text(), "MISMATCH");
                break;
            case "VIEW_LOTS_SYMBOL_B":
            case "VIEW_LOTS_SYMBOL_C":
                p = statusTd.child(0);
                Assert.assertNotNull(p);
                Assert.assertTrue(p.attr("style").endsWith("Red"));
                Assert.assertEquals(statusTd.text(), "MISSING");
                break;
            case "VIEW_LOTS_SYMBOL_D":
                Assert.assertEquals(statusTd.text(), "OK");
                break;
        }
    }

    public void testDoGet() throws Exception {
        EtradeOrdersDataFetcher mockOrdersFetcher = Mockito.mock(EtradeOrdersDataFetcher.class);
        Map<String, List<Order>> symbolToOrdersIndex = new HashMap<>();
        List<Order> sellOrderListA = new LinkedList<>();
        sellOrderListA.add(new Order());
        symbolToOrdersIndex.put("VIEW_LOTS_SYMBOL_A", sellOrderListA);
        List<Order> sellOrderListD = new LinkedList<>();
        sellOrderListD.add(new Order());
        symbolToOrdersIndex.put("VIEW_LOTS_SYMBOL_D", sellOrderListD);
        Mockito.doReturn(symbolToOrdersIndex).when(mockOrdersFetcher).getSymbolToSellOrdersIndex();

        EtradePortfolioDataFetcher mockPortfolioFetcher = Mockito.mock(EtradePortfolioDataFetcher.class);
        Map<String, List<PositionLotsResponse.PositionLot>> symbolToLotsIndex = new HashMap<>();
        Mockito.doReturn(symbolToLotsIndex).when(mockPortfolioFetcher).getSymbolToLotsIndex();

        List<PositionLotsResponse.PositionLot> lotListA = new LinkedList<>();
        PositionLotsResponse.PositionLot lotA1 = new PositionLotsResponse.PositionLot();
        lotA1.setSymbol("VIEW_LOTS_SYMBOL_A");
        lotA1.setPrice(150.00F);
        lotListA.add(lotA1);
        PositionLotsResponse.PositionLot lotA2 = new PositionLotsResponse.PositionLot();
        lotA2.setSymbol("VIEW_LOTS_SYMBOL_A");
        lotA2.setPrice(100.00F);
        lotA2.setTotalCostForGainPct(1.00F);
        lotA2.setTotalLotCount(2);
        lotA2.setPositionPctOfPortfolio(1.00F);
        lotA2.setTotalPositionCost(1000.00F);
        lotA2.setMarketValue(1010.00F);
        lotA2.setAcquiredDate(System.currentTimeMillis());
        lotListA.add(lotA2);
        symbolToLotsIndex.put("VIEW_LOTS_SYMBOL_A", lotListA);

        List<PositionLotsResponse.PositionLot> lotListB = new LinkedList<>();
        PositionLotsResponse.PositionLot lotB1 = new PositionLotsResponse.PositionLot();
        lotB1.setSymbol("VIEW_LOTS_SYMBOL_B");
        lotB1.setPrice(150.00F);
        lotListB.add(lotB1);
        PositionLotsResponse.PositionLot lotB2 = new PositionLotsResponse.PositionLot();
        lotB2.setSymbol("VIEW_LOTS_SYMBOL_B");
        lotB2.setPrice(100.00F);
        lotB2.setTotalCostForGainPct(4.00F);
        lotB2.setTotalLotCount(2);
        lotB2.setPositionPctOfPortfolio(1.00F);
        lotB2.setTotalPositionCost(1000.00F);
        lotB2.setMarketValue(1040.00F);
        lotB2.setAcquiredDate(System.currentTimeMillis());
        lotListB.add(lotB2);
        symbolToLotsIndex.put("VIEW_LOTS_SYMBOL_B", lotListB);

        List<PositionLotsResponse.PositionLot> lotListC = new LinkedList<>();
        PositionLotsResponse.PositionLot lotC1 = new PositionLotsResponse.PositionLot();
        lotC1.setSymbol("VIEW_LOTS_SYMBOL_C");
        lotC1.setPrice(150.00F);
        lotListC.add(lotC1);
        PositionLotsResponse.PositionLot lotC2 = new PositionLotsResponse.PositionLot();
        lotC2.setSymbol("VIEW_LOTS_SYMBOL_C");
        lotC2.setPrice(100.00F);
        lotC2.setTotalCostForGainPct(-1.00F);
        lotC2.setTotalLotCount(2);
        lotC2.setPositionPctOfPortfolio(1.00F);
        lotC2.setTotalPositionCost(1000.00F);
        lotC2.setMarketValue(990.00F);
        lotC2.setAcquiredDate(System.currentTimeMillis());
        lotListC.add(lotC2);
        symbolToLotsIndex.put("VIEW_LOTS_SYMBOL_C", lotListC);

        List<PositionLotsResponse.PositionLot> lotListD = new LinkedList<>();
        PositionLotsResponse.PositionLot lotD1 = new PositionLotsResponse.PositionLot();
        lotD1.setSymbol("VIEW_LOTS_SYMBOL_D");
        lotD1.setPrice(150.00F);
        lotListD.add(lotD1);
        PositionLotsResponse.PositionLot lotD2 = new PositionLotsResponse.PositionLot();
        lotD2.setSymbol("VIEW_LOTS_SYMBOL_D");
        lotD2.setPrice(100.00F);
        lotD2.setTotalCostForGainPct(-4.00F);
        lotD2.setTotalLotCount(1);
        lotD2.setPositionPctOfPortfolio(1.00F);
        lotD2.setTotalPositionCost(1000.00F);
        lotD2.setMarketValue(960.00F);
        lotD2.setAcquiredDate(System.currentTimeMillis());
        lotListD.add(lotD2);
        symbolToLotsIndex.put("VIEW_LOTS_SYMBOL_D", lotListD);

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
            Element thead = table.child(0);
            Assert.assertNotNull(thead);
            Assert.assertEquals(thead.tagName(), "thead");
            Element tbody = table.child(1);
            Assert.assertNotNull(tbody);
            Elements rows = tbody.children();
            Assert.assertEquals(rows.size(), 4);

            Element tr1 = tbody.child(0);
            Assert.assertNotNull(tr1);
            Element tr1td1 = tr1.child(0);
            Assert.assertNotNull(tr1td1);
            checkColor(tr1, tr1td1.text());
            Element tr1td2 = tr1.child(1);
            Assert.assertNotNull(tr1td2);
            Element tr1td3 = tr1.child(2);
            Assert.assertNotNull(tr1td3);
            checkStatus(tr1td2, tr1td3);

            Element tr2 = tbody.child(1);
            Assert.assertNotNull(tr2);
            Element tr2td1 = tr2.child(0);
            Assert.assertNotNull(tr2td1);
            checkColor(tr2, tr2td1.text());
            Element tr2td2 = tr2.child(1);
            Assert.assertNotNull(tr2td2);
            Element tr2td3 = tr2.child(2);
            Assert.assertNotNull(tr2td3);
            checkStatus(tr2td2, tr2td3);

            Element tr3 = tbody.child(2);
            Assert.assertNotNull(tr3);
            Element tr3td1 = tr3.child(0);
            Assert.assertNotNull(tr3td1);
            checkColor(tr3, tr3td1.text());
            Element tr3td2 = tr3.child(1);
            Assert.assertNotNull(tr3td2);
            Element tr3td3 = tr3.child(2);
            Assert.assertNotNull(tr3td3);
            checkStatus(tr3td2, tr3td3);

            Element tr4 = tbody.child(3);
            Assert.assertNotNull(tr4);
            Element tr4td1 = tr4.child(0);
            Assert.assertNotNull(tr4td1);
            checkColor(tr4, tr4td1.text());
            Element tr4td2 = tr4.child(1);
            Assert.assertNotNull(tr4td2);
            Element tr4td3 = tr4.child(2);
            Assert.assertNotNull(tr4td3);
            checkStatus(tr4td2, tr4td3);
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
        Mockito.doReturn(mockPortfolioFetcher).when(mockLifecycleListener).getPortfolioDataFetcher();

        ViewEtradeLotsServlet viewLotsServlet = new ViewEtradeLotsServlet();
        viewLotsServlet.setLifecycleListener(mockLifecycleListener);

        viewLotsServlet.doGet(mockRequest, mockResponse);
        Mockito.verify(mockPrintWriter).print(Mockito.anyString());
    }
}
