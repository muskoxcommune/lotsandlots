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
                    value = "Page number of the desired portfolio results page.",
                    example = "2")})
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
        Message portfolioMessage = new Message();
        portfolioMessage.setRequiresOauth(true);
        portfolioMessage.setHttpMethod("GET");
        portfolioMessage.setUrl(API.getBaseUrl() + API.getOrdersUri());
        String marker = request.getParameter("marker");
        if (!StringUtils.isBlank(marker)) {
            if (StringUtils.isNumeric(marker)) {
                portfolioMessage.setQueryString("marker=" + marker);
            } else {
                throw new InvalidParameterException(
                        "Query parameter 'marker' should have a numeric value, got: '" + marker + "'");
            }
        }
        return portfolioMessage;
    }
}
