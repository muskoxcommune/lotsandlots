package io.lotsandlots.web.servlet;

import io.lotsandlots.etrade.EtradeOrdersDataFetcher;
import io.lotsandlots.etrade.model.Order;
import io.lotsandlots.util.DateFormatter;
import io.lotsandlots.util.HtmlHelper;
import io.lotsandlots.web.listener.LifecycleListener;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.util.HtmlUtils;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Api(value = "/view/etrade")
public class ViewEtradeOrdersServlet extends HttpServlet {

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.00");

    private LifecycleListener lifecycleListener = LifecycleListener.getListener();

    @ApiOperation(
            httpMethod = "GET",
            value = "Get view of E*Trade orders.",
            nickname = "orders")
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pageLength = request.getParameter("pageLength");
        if (!StringUtils.isNumeric(pageLength)) {
            pageLength = "999";
        }
        String symbol = request.getParameter("symbol");

        List<Order> ordersToDisplay = new LinkedList<>();
        EtradeOrdersDataFetcher ordersDataFetcher = lifecycleListener.getEtradeOrdersDataFetcher();
        if (ordersDataFetcher != null) {
            if (StringUtils.isBlank(symbol)) {
                for (List<Order> buyOrders : ordersDataFetcher.getSymbolToBuyOrdersIndex().values()) {
                    ordersToDisplay.addAll(buyOrders);
                }
                for (List<Order> sellOrders : ordersDataFetcher.getSymbolToSellOrdersIndex().values()) {
                    ordersToDisplay.addAll(sellOrders);
                }
            } else {
                for (Map.Entry<String, List<Order>> entry :
                        ordersDataFetcher.getSymbolToBuyOrdersIndex().entrySet()) {
                    List<Order> buyOrders = entry.getValue();
                    if (!entry.getKey().equals(symbol.toUpperCase())) {
                        continue;
                    }
                    ordersToDisplay.addAll(buyOrders);
                }
                for (Map.Entry<String, List<Order>> entry :
                        ordersDataFetcher.getSymbolToSellOrdersIndex().entrySet()) {
                    List<Order> sellOrders = entry.getValue();
                    if (!entry.getKey().equals(symbol.toUpperCase())) {
                        continue;
                    }
                    ordersToDisplay.addAll(sellOrders);
                }
            }
        }

        StringBuilder htmlBuilder = new StringBuilder();
        htmlBuilder.append("<html>");
        htmlBuilder.append("<head>");
        htmlBuilder.append("<title>").append(
                (symbol != null) ? HtmlUtils.htmlEscape(symbol) + " orders" : "Orders").append("</title>");
        HtmlHelper.appendDataTablesTags(htmlBuilder);
        HtmlHelper.appendDataTablesFeatures(htmlBuilder, "orders",
                "\"order\": [[0, \"desc\"]],", "\"pageLength\": " + HtmlUtils.htmlEscape(pageLength));
        htmlBuilder.append("</head>");
        htmlBuilder.append("<body>");

        htmlBuilder.append("<table id=\"orders\" class=\"display\" style=\"width:100%\">");
        HtmlHelper.appendTableHeaderRow(htmlBuilder,
                "datePlaced",
                "symbol",
                "quantity",
                "limit",
                "action",
                "status"
        );
        htmlBuilder.append("<tbody>");
        for (Order order : ordersToDisplay) {
            htmlBuilder.append("<tr>");
            htmlBuilder.append("<td>")
                       .append(DateFormatter.epochSecondsToDateString(order.getPlacedTime() / 1000L))
                       .append("</td>");
            htmlBuilder.append("<td>").append(order.getSymbol()).append("</td>");
            htmlBuilder.append("<td>").append(order.getOrderedQuantity()).append("</td>");
            htmlBuilder.append("<td>$").append(DECIMAL_FORMAT.format(order.getLimitPrice())).append("</td>");
            htmlBuilder.append("<td>").append(order.getOrderAction()).append("</td>");
            htmlBuilder.append("<td>").append(order.getStatus()).append("</td>");
            htmlBuilder.append("</tr>");
        }
        htmlBuilder.append("</tbody>");
        htmlBuilder.append("</table");

        htmlBuilder.append("</body>");
        htmlBuilder.append("</html>");
        response.getWriter().print(htmlBuilder.substring(0, htmlBuilder.length() - 1));
    }

    void setLifecycleListener(LifecycleListener lifecycleListener) {
        this.lifecycleListener = lifecycleListener;
    }
}
