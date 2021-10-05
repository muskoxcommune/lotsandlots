package io.lotsandlots.web.servlet;

import io.lotsandlots.etrade.EtradeRestTemplateFactory;
import io.lotsandlots.etrade.Message;
import io.lotsandlots.etrade.api.ApiConfig;
import io.lotsandlots.etrade.api.PositionLotsResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Api(value = "/etrade")
public class EtradeLotServlet extends HttpServlet implements EtradeApiServlet {

    private static final ApiConfig API = EtradeRestTemplateFactory.getClient().getApiConfig();
    private static final Logger LOG = LoggerFactory.getLogger(EtradeLotServlet.class);

    @ApiOperation(
            httpMethod = "GET",
            value = "Get lots for a single E*Trade position.",
            nickname = "lots")
    @ApiImplicitParams(
            {@ApiImplicitParam(name = "positionId", dataType = "integer", paramType = "query", required = true,
                    value = "ID of desired position.",
                    example = "100000000000")})
    @ApiResponses({
            @ApiResponse(code = 200, message = "Lot data returned by E*Trade."),
            @ApiResponse(code = 400, message = "If OAuth tokens have not been initialized."),
            @ApiResponse(code = 500, message = "If unable to return lot data.")})
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        long timeStartedMillis = System.currentTimeMillis();
        doEtradeGet(request, response);
        LOG.info("Completed request in {}ms", System.currentTimeMillis() - timeStartedMillis);
    }

    @Override
    public void handleException(HttpServletResponse response, Exception e) throws IOException {
        LOG.error("Failed to fetch lots", e);
        if (e instanceof InvalidParameterException) {
            response.sendError(400, e.getMessage());
        } else {
            response.sendError(500, "Unable to fetch lots");
        }
    }

    @Override
    public Message newMessage(HttpServletRequest request) throws InvalidParameterException {
        Message portfolioMessage = new Message();
        portfolioMessage.setRequiresOauth(true);
        portfolioMessage.setHttpMethod("GET");
        String positionId = request.getParameter("positionId");
        if (positionId != null) {
            if (StringUtils.isNumeric(positionId)) {
                portfolioMessage.setUrl(API.getBaseUrl() + API.getPortfolioUri() + "/" + positionId);
            } else {
                throw new InvalidParameterException(
                        "Query parameter 'positionId' should have a numeric value, got: '" + positionId + "'");
            }
        } else {
            throw new InvalidParameterException("Query parameter 'positionId' is required");
        }
        return portfolioMessage;
    }
}
