package io.lotsandlots.web.servlet;

import io.lotsandlots.etrade.api.ApiConfig;
import io.lotsandlots.etrade.rest.EtradeRestTemplateFactory;
import io.lotsandlots.etrade.rest.Message;
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
public class EtradePortfolioServlet extends HttpServlet implements EtradeApiServlet {

    private static final ApiConfig API = EtradeRestTemplateFactory.getTemplateFactory().getApiConfig();
    private static final Logger LOG = LoggerFactory.getLogger(EtradePortfolioServlet.class);

    @ApiOperation(
            httpMethod = "GET",
            value = "Get a single E*Trade portfolio.",
            nickname = "portfolio")
    @ApiImplicitParams(
            {@ApiImplicitParam(name = "nextPageNo", dataType = "integer", paramType = "query",
                    value = "Page number of the desired portfolio results page.",
                    example = "2")})
    @ApiResponses({
            @ApiResponse(code = 200, message = "Portfolio data returned by E*Trade."),
            @ApiResponse(code = 400, message = "If OAuth tokens have not been initialized."),
            @ApiResponse(code = 500, message = "If unable to return portfolio data.")})
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        long timeStartedMillis = System.currentTimeMillis();
        doEtradeGet(request, response);
        LOG.info("Completed request in {}ms", System.currentTimeMillis() - timeStartedMillis);    }

    @Override
    public void handleException(HttpServletResponse response, Exception e) throws IOException {
        LOG.error("Failed to fetch portfolio", e);
        if (e instanceof InvalidParameterException) {
            response.sendError(400, e.getMessage());
        } else {
            response.sendError(500, "Unable to fetch portfolio");
        }
    }

    @Override
    public Message newMessage(HttpServletRequest request) throws InvalidParameterException {
        Message portfolioMessage = new Message();
        portfolioMessage.setRequiresOauth(true);
        portfolioMessage.setHttpMethod("GET");

        String portfolioUrl = API.getPortfolioUrl();
        if (portfolioUrl == null) {
            throw new InvalidParameterException("Please configure etrade.accountIdKey");
        }
        portfolioMessage.setUrl(portfolioUrl);

        String nextPageNo = request.getParameter("nextPageNo");
        String portfolioQueryString = API.getPortfolioQueryString();
        if (!StringUtils.isBlank(nextPageNo)) {
            if (StringUtils.isNumeric(nextPageNo)) {
                portfolioQueryString += "&pageNumber=" + nextPageNo;
            } else {
                throw new InvalidParameterException(
                        "Query parameter 'nextPageNo' should have a numeric value, got: '" + nextPageNo + "'");
            }
        }
        portfolioMessage.setQueryString(portfolioQueryString);
        return portfolioMessage;
    }
}
