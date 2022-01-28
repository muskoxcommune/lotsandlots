package io.lotsandlots.web.servlet;

import io.lotsandlots.etrade.EtradeOrdersDataFetcher;
import io.lotsandlots.etrade.EtradePortfolioDataFetcher;
import io.lotsandlots.etrade.api.OrdersResponse;
import io.lotsandlots.etrade.api.PositionLotsResponse;
import io.lotsandlots.util.DateFormatter;
import io.lotsandlots.util.HtmlHelper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Api(value = "/view/etrade")
public class ViewEtradeLotsServlet extends HttpServlet {

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.00");

    @ApiOperation(
            httpMethod = "GET",
            value = "Get view of E*Trade lots.",
            nickname = "lots")
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pageLength = request.getParameter("pageLength");
        String showAllLots = request.getParameter("showAllLots");
        String symbol = request.getParameter("symbol");

        List<PositionLotsResponse.PositionLot> includedLots = new LinkedList<>();
        for (Map.Entry<String, List<PositionLotsResponse.PositionLot>> entry :
                EtradePortfolioDataFetcher.getSymbolToLotsIndex().entrySet()) {
            List<PositionLotsResponse.PositionLot> lots = entry.getValue();
            if (!StringUtils.isBlank(symbol) && !entry.getKey().equals(symbol.toUpperCase())) {
                continue;
            }
            if (showAllLots != null && showAllLots.equals("true")) {
                includedLots.addAll(lots);
            } else {
                PositionLotsResponse.PositionLot lowestPricedLot = null;
                for (PositionLotsResponse.PositionLot lot : lots) {
                    if (lowestPricedLot == null || lot.getPrice() < lowestPricedLot.getPrice()) {
                        lowestPricedLot = lot;
                    }
                }
                includedLots.add(lowestPricedLot);
            }
        }
        Map<String, List<OrdersResponse.Order>> symbolToOrdersIndex =
                EtradeOrdersDataFetcher.EMPTY_SYMBOL_TO_ORDERS_INDEX;
        EtradeOrdersDataFetcher ordersDataFetcher = EtradeOrdersDataFetcher.getDataFetcher();
        if (ordersDataFetcher != null) {
            symbolToOrdersIndex =
                    EtradeOrdersDataFetcher.getDataFetcher().getSymbolToSellOrdersIndex();
        }

        if (pageLength == null) {
            pageLength = "999";
        }

        StringBuilder htmlBuilder = new StringBuilder();
        htmlBuilder.append("<html>");

        htmlBuilder.append("<head>");
        htmlBuilder.append("<title>").append((symbol != null) ? symbol + " lots" : "Lots").append("</title>");
        HtmlHelper.appendDataTablesTags(htmlBuilder);
        HtmlHelper.appendDataTablesFeatures(htmlBuilder, "lots",
                "\"order\": [[0, \"desc\"]],", "\"pageLength\": " + pageLength);
        htmlBuilder.append("</head>");
        htmlBuilder.append("<body>");

        htmlBuilder.append("<table id=\"lots\" class=\"display\" style=\"width:100%\">");
        HtmlHelper.appendTableHeaderRow(htmlBuilder,
                "% unrealized",
                "orderStatus",
                "symbol",
                "% exposed",
                "$ exposed",
                "lotValue",
                "dateAcquired"
        );
        htmlBuilder.append("<tbody>");
        for (PositionLotsResponse.PositionLot lot : includedLots) {
            htmlBuilder.append("<tr");
            if (lot.getTotalCostForGainPct() > 0) {
                if (lot.getTotalCostForGainPct() > 3) {
                    htmlBuilder.append(" style=\"background-color: MediumSeaGreen\"");
                } else {
                    htmlBuilder.append(" style=\"background-color: PaleGreen\"");
                }
            } else {
                if (lot.getTotalCostForGainPct() < -3) {
                    htmlBuilder.append(" style=\"background-color: LightCoral\"");
                } else {
                    htmlBuilder.append(" style=\"background-color: Pink\"");
                }
            }
            htmlBuilder.append(">");

            htmlBuilder.append("<td>").append(DECIMAL_FORMAT.format(lot.getTotalCostForGainPct())).append("%</td>");
            if (!symbolToOrdersIndex.containsKey(lot.getSymbol())) {
                htmlBuilder.append("<td>").append("<p style=\"color:Red\"><b>MISSING</b></p>").append("</td>");
            } else {
                List<OrdersResponse.Order> orders = symbolToOrdersIndex.get(lot.getSymbol());
                if (orders.size() == lot.getTotalLotCount()) {
                    htmlBuilder.append("<td>").append("OK").append("</td>");
                } else {
                    htmlBuilder.append("<td>").append("<p style=\"color:Red\"><b>MISMATCH</b></p>").append("</td>");
                }
            }
            htmlBuilder.append("<td>").append(lot.getSymbol()).append("</td>");
            htmlBuilder.append("<td>").append(DECIMAL_FORMAT.format(lot.getPositionPctOfPortfolio())).append("%</td>");
            htmlBuilder.append("<td>$").append(DECIMAL_FORMAT.format(lot.getTotalPositionCost())).append("</td>");
            htmlBuilder.append("<td>$").append(DECIMAL_FORMAT.format(lot.getMarketValue())).append("</td>");
            if (lot.getAcquiredDate() != null) {
                htmlBuilder.append("<td>")
                        .append(DateFormatter.epochSecondsToDateString(lot.getAcquiredDate() / 1000L))
                        .append("</td>");
            } else {
                htmlBuilder.append("<td>")
                        .append("null")
                        .append("</td>");
            }
            htmlBuilder.append("</tr>");
        }
        htmlBuilder.append("</tbody>");
        htmlBuilder.append("</table");

        htmlBuilder.append("</body>");
        htmlBuilder.append("</html>");
        response.getWriter().print(htmlBuilder.substring(0, htmlBuilder.length() - 1));
    }
}
