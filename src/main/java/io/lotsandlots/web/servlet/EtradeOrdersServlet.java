package io.lotsandlots.web.servlet;

import io.lotsandlots.etrade.rest.EtradeRestTemplateFactory;
import io.lotsandlots.etrade.rest.Message;
import io.lotsandlots.etrade.api.ApiConfig;
import io.lotsandlots.util.DateFormatter;
import io.lotsandlots.web.error.InvalidParameterException;
import io.swagger.annotations.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Api(value = "/etrade")
public class EtradeOrdersServlet extends HttpServlet implements EtradeApiServlet {

    private static final ApiConfig API = EtradeRestTemplateFactory.getTemplateFactory().getApiConfig();
    private static final Logger LOG = LoggerFactory.getLogger(EtradeOrdersServlet.class);

    @ApiOperation(
            httpMethod = "GET",
            value = "Get E*Trade orders.",
            nickname = "orders")
    @ApiImplicitParams(
            {@ApiImplicitParam(name = "marker", dataType = "integer", paramType = "query",
                    value = "Specifies the desired starting point of the set of orders to return.",
                    example = "1633362976972"),
            @ApiImplicitParam(name = "status", dataType = "string", paramType = "query",
                    value = "The status: OPEN, EXECUTED, CANCELLED, INDIVIDUAL_FILLS, CANCEL_REQUESTED, EXPIRED, "
                            + "REJECTED, PARTIAL, DO_NOT_EXERCISE, DONE_TRADE_EXECUTED",
                    example = "OPEN"),
            @ApiImplicitParam(name = "symbol", dataType = "string", paramType = "query",
                    value = "The market symbol for the security being bought or sold. Supports up to 25 symbols "
                            + "seperated by delimiter ','.",
                    example = "1633362976972"),
            @ApiImplicitParam(name = "transactionType", dataType = "string", paramType = "query",
                    value = "Type of transaction: ATNM, BUY, SELL, SELL_SHORT, BUY_TO_COVER, MF_EXCHANGE",
                    example = "SELL")})
    @ApiResponses({
            @ApiResponse(code = 200, message = "Orders data returned by E*Trade."),
            @ApiResponse(code = 400, message = "If OAuth tokens have not been initialized."),
            @ApiResponse(code = 500, message = "If unable to return orders data.")})
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        long timeStartedMillis = System.currentTimeMillis();
        doEtradeGet(request, response);
        LOG.info("Completed request in {}ms", System.currentTimeMillis() - timeStartedMillis);
    }

    @Override
    public void handleException(HttpServletResponse response, Exception e) throws IOException {
        LOG.error("Failed to fetch orders", e);
        if (e instanceof InvalidParameterException) {
            response.sendError(400, "Invalid parameter");
        } else {
            response.sendError(500, "Unable to fetch orders");
        }
    }

    @Override
    public Message newMessage(HttpServletRequest request) throws InvalidParameterException {
        Message ordersMessage = new Message();
        ordersMessage.setRequiresOauth(true);
        ordersMessage.setHttpMethod("GET");

        String ordersUrl = API.getOrdersUrl();
        if (ordersUrl == null) {
            throw new InvalidParameterException("Please configure etrade.accountIdKey");
        }
        ordersMessage.setUrl(ordersUrl);
        String ordersQueryString = API.getOrdersQueryString();

        long currentTimeMillis = System.currentTimeMillis();
        // 60 seconds * 60 minutes * 24 hours * 180 days = 15552000 seconds
        ordersQueryString += "&fromDate=" + DateFormatter.epochSecondsToDateString(
                (currentTimeMillis  / 1000L) - 15552000, "MMddyyyy");
        ordersQueryString += "&toDate=" + DateFormatter.epochSecondsToDateString(
                currentTimeMillis / 1000L, "MMddyyyy");

        String marker = request.getParameter("marker");
        String status = request.getParameter("status");
        String symbol = request.getParameter("symbol");
        String transactionType = request.getParameter("transactionType");
        if (!StringUtils.isBlank(marker)) {
            if (StringUtils.isNumeric(marker)) {
                ordersQueryString += "&marker=" + marker;
            } else {
                throw new InvalidParameterException(
                        "Query parameter 'marker' should have a numeric value, got: '" + marker + "'");
            }
        }
        if (!StringUtils.isBlank(status)) {
            ordersQueryString += "&status=" + status;
        }
        if (!StringUtils.isBlank(symbol)) {
            ordersQueryString += "&symbol=" + symbol;
        }
        if (!StringUtils.isBlank(transactionType)) {
            ordersQueryString += "&transactionType=" + transactionType;
        }
        ordersMessage.setQueryString(ordersQueryString);
        return ordersMessage;
    }
}
