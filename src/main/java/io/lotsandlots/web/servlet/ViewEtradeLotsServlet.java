package io.lotsandlots.web.servlet;

import io.lotsandlots.etrade.EtradePortfolioDataFetcher;
import io.lotsandlots.etrade.api.PositionLotsResponse;
import io.lotsandlots.util.DateFormatter;
import io.lotsandlots.util.HtmlHelper;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ViewEtradeLotsServlet extends HttpServlet {

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.00");

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pageLength = request.getParameter("pageLength");
        String showAllLots = request.getParameter("showAllLots");
        String symbol = request.getParameter("symbol");

        List<PositionLotsResponse.PositionLot> includedLots = new LinkedList<>();
        Map<String, List<PositionLotsResponse.PositionLot>> lotsCache = EtradePortfolioDataFetcher.getLots();
        for (Map.Entry<String, List<PositionLotsResponse.PositionLot>> entry : lotsCache.entrySet()) {
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
        if (pageLength == null) {
            pageLength = "999";
        }

        StringBuilder htmlBuilder = new StringBuilder();
        htmlBuilder.append("<html>");

        htmlBuilder.append("<head>");
        htmlBuilder.append("<title>").append((symbol != null) ? symbol + " lots" : "Lots").append("</title>");
        HtmlHelper.appendDataTablesTags(htmlBuilder);
        HtmlHelper.appendDataTablesFeatures(htmlBuilder, "lots",
                "\"order\": [[7, \"desc\"]],", "\"pageLength\": " + pageLength);
        htmlBuilder.append("</head>");
        htmlBuilder.append("<body>");

        htmlBuilder.append("<table id=\"lots\" class=\"display\" style=\"width:100%\">");
        HtmlHelper.appendTableHeaderRow(htmlBuilder,
                "acquired", "symbol", "lots", "exposure", "total", "price", "target", "unrealized");
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
            htmlBuilder.append("<td>")
                       .append(DateFormatter.epochSecondsToDateString(lot.getAcquiredDate() / 1000L))
                       .append("</td>");
            htmlBuilder.append("<td>").append(lot.getSymbol()).append("</td>");
            htmlBuilder.append("<td>").append(lot.getTotalLotCount()).append("</td>");
            htmlBuilder.append("<td>").append(DECIMAL_FORMAT.format(lot.getPositionPctOfPortfolio())).append("%</td>");
            htmlBuilder.append("<td>$").append(DECIMAL_FORMAT.format(lot.getTotalPositionCost())).append("</td>");
            htmlBuilder.append("<td>$").append(DECIMAL_FORMAT.format(lot.getPrice())).append("</td>");
            htmlBuilder.append("<td>$").append(DECIMAL_FORMAT.format(lot.getTargetPrice())).append("</td>");
            htmlBuilder.append("<td>").append(DECIMAL_FORMAT.format(lot.getTotalCostForGainPct())).append("%</td>");
            htmlBuilder.append("</tr>");
        }
        htmlBuilder.append("</tbody>");
        htmlBuilder.append("</table");

        htmlBuilder.append("</body>");
        htmlBuilder.append("</html>");
        response.getWriter().print(htmlBuilder.substring(0, htmlBuilder.length() - 1));
    }
}
