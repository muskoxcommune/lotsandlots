package io.lotsandlots.web.servlet;

import com.google.common.cache.Cache;
import io.lotsandlots.etrade.EtradeOrdersDataFetcher;
import io.lotsandlots.etrade.api.OrdersResponse;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

public class ViewEtradeOrdersServlet extends HttpServlet {

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        Cache<String, List<OrdersResponse.Order>> sellOrdersCache = EtradeOrdersDataFetcher.getSellOrders();
        response.getWriter().print("Woot!");
    }
}
