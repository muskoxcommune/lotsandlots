package io.lotsandlots.web.servlet;

import io.lotsandlots.etrade.ApiConfig;
import io.lotsandlots.etrade.EtradeRestTemplateFactory;
import io.lotsandlots.etrade.Message;
import io.swagger.annotations.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Api(value = "/etrade")
public class EtradePortfolioServlet extends HttpServlet implements EtradeServlet {

    private static final ApiConfig API = EtradeRestTemplateFactory.getClient().getApiConfig();
    private static final Logger LOG = LoggerFactory.getLogger(EtradePortfolioServlet.class);

    @ApiOperation(
            httpMethod = "GET",
            value = "Get a single E*Trade portfolio.",
            nickname = "portfolio")
    @ApiImplicitParams(
            {@ApiImplicitParam(name = "nextPageNo", dataType = "integer", paramType = "query", required = false,
                    value = "Page number of the desired portfolio results page.",
                    example = "2")})
    @ApiResponses({
            @ApiResponse(code = 200, message = "Portfolio data returned by E*Trade."),
            @ApiResponse(code = 400, message = "If OAuth tokens have not been initialized."),
            @ApiResponse(code = 500, message = "If unable to return portfolio data.")})
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        doEtradeGet(request, response);
    }

    @Override
    public void handleException(HttpServletResponse response, Exception e) throws IOException {
        LOG.error("Failed to fetch portfolio", e);
        response.sendError(500, "Unable to fetch portfolio");
    }

    @Override
    public Message newMessage(HttpServletRequest request) {
        Message portfolioMessage = new Message();
        portfolioMessage.setRequiresOauth(true);
        portfolioMessage.setHttpMethod("GET");
        portfolioMessage.setUrl(API.getBaseUrl() + API.getPortfolioUri());
        String nextPageNo = request.getParameter("nextPageNo");
        String portfolioQueryString = API.getPortfolioQueryString();
        if (!StringUtils.isBlank(nextPageNo) && StringUtils.isNumeric(nextPageNo)) {
            portfolioQueryString += "&pageNumber=" + nextPageNo;
        }
        portfolioMessage.setQueryString(portfolioQueryString);
        return portfolioMessage;
    }
}
