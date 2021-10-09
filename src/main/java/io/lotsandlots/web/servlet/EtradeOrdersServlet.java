package io.lotsandlots.web.servlet;

import io.lotsandlots.etrade.EtradeRestTemplateFactory;
import io.lotsandlots.etrade.Message;
import io.lotsandlots.etrade.api.ApiConfig;
import io.swagger.annotations.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

@Api(value = "/etrade")
public class EtradeOrdersServlet extends HttpServlet implements EtradeApiServlet {

    private static final ApiConfig API = EtradeRestTemplateFactory.getClient().getApiConfig();
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
        LOG.info("Completed request in {}ms", System.currentTimeMillis() - timeStartedMillis);    }

    @Override
    public void handleException(HttpServletResponse response, Exception e) throws IOException {
        LOG.error("Failed to fetch orders", e);
        if (e instanceof InvalidParameterException) {
            response.sendError(400, e.getMessage());
        } else {
            response.sendError(500, "Unable to fetch orders");
        }
    }

    @Override
    public Message newMessage(HttpServletRequest request) throws InvalidParameterException {
        Message ordersMessage = new Message();
        ordersMessage.setRequiresOauth(true);
        ordersMessage.setHttpMethod("GET");
        ordersMessage.setUrl(API.getBaseUrl() + API.getOrdersUri());
        String ordersQueryString = API.getOrdersQueryString();
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
