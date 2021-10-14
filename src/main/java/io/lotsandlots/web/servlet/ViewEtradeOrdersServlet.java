package io.lotsandlots.web.servlet;

import com.google.common.cache.Cache;
import io.lotsandlots.etrade.EtradeOrdersDataFetcher;
import io.lotsandlots.etrade.api.OrdersResponse;
import io.lotsandlots.util.DateFormatter;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ViewEtradeOrdersServlet extends HttpServlet {

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.00");

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pageLength = request.getParameter("pageLength");
        String symbol = request.getParameter("symbol");

        List<OrdersResponse.Order> includedOrders = new LinkedList<>();
        Cache<String, List<OrdersResponse.Order>> sellOrdersCache = EtradeOrdersDataFetcher.getSellOrders();
        for (Map.Entry<String, List<OrdersResponse.Order>> entry : sellOrdersCache.asMap().entrySet()) {
            List<OrdersResponse.Order> orders = entry.getValue();
            if (!StringUtils.isBlank(symbol) && !entry.getKey().equals(symbol.toUpperCase())) {
                continue;
            }
            includedOrders.addAll(orders);
        }
        if (pageLength == null) {
            pageLength = "999";
        }

        StringBuilder htmlBuilder = new StringBuilder();
        htmlBuilder.append("<html>");

        htmlBuilder.append("<head>");
        htmlBuilder.append("<title>").append((symbol != null) ? symbol + " orders" : "Orders").append("</title>");
        htmlBuilder.append("<link rel=\"stylesheet\" href=\"https://cdn.datatables.net/1.11.3/css/jquery.dataTables.min.css\">");
        htmlBuilder.append("<script src=\"https://code.jquery.com/jquery-3.5.1.js\"></script>");
        htmlBuilder.append("<script src=\"https://cdn.datatables.net/1.11.3/js/jquery.dataTables.min.js\"></script>");
        htmlBuilder.append("<script>").append("$(document).ready(function() {").append("$('#lots').DataTable({");

        // DataTable settings
        //htmlBuilder.append("\"order\": [[6, \"desc\"]],"); // Increment when adding new <th>
        htmlBuilder.append("\"pageLength\": ").append(pageLength);

        htmlBuilder.append("});").append("});").append("</script>");
        htmlBuilder.append("</head>");
        htmlBuilder.append("<body>");

        htmlBuilder.append("<table id=\"lots\" class=\"display\" style=\"width:100%\">");
        htmlBuilder.append("<thead>");
        htmlBuilder.append("<tr>");
        htmlBuilder.append("<th>placed</th>");
        htmlBuilder.append("<th>symbol</th>");
        htmlBuilder.append("<th>quantity</th>");
        htmlBuilder.append("<th>filled</th>");
        htmlBuilder.append("<th>limit</th>");
        htmlBuilder.append("<th>value</th>");
        htmlBuilder.append("<th>status</th>");
        htmlBuilder.append("</tr>");
        htmlBuilder.append("</thead>");
        htmlBuilder.append("<tbody>");
        for (OrdersResponse.Order order : includedOrders) {
            htmlBuilder.append("<tr>");
            htmlBuilder.append("<td>")
                       .append(DateFormatter.epochSecondsToDateString(order.getPlacedTime() / 1000L))
                       .append("</td>");
            htmlBuilder.append("<td>").append(order.getSymbol()).append("</td>");
            htmlBuilder.append("<td>").append(order.getOrderedQuantity()).append("</td>");
            htmlBuilder.append("<td>").append(order.getFilledQuantity()).append("</td>");
            htmlBuilder.append("<td>$").append(DECIMAL_FORMAT.format(order.getLimitPrice())).append("</td>");
            htmlBuilder.append("<td>$").append(DECIMAL_FORMAT.format(order.getOrderValue())).append("</td>");
            htmlBuilder.append("<td>").append(order.getStatus()).append("</td>");
            htmlBuilder.append("</tr>");
        }
        htmlBuilder.append("</tbody>");
        htmlBuilder.append("</table");

        htmlBuilder.append("</body>");
        htmlBuilder.append("</html>");
        response.getWriter().print(htmlBuilder.substring(0, htmlBuilder.length() - 1));
    }
}
