package io.lotsandlots.web.servlet;

import io.lotsandlots.etrade.EtradeRestTemplateFactory;
import io.lotsandlots.etrade.api.ApiConfig;
import io.lotsandlots.etrade.Message;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Api(value = "/etrade")
public class EtradeAccountListServlet extends HttpServlet implements EtradeApiServlet {

    private static final ApiConfig API = EtradeRestTemplateFactory.getClient().getApiConfig();
    private static final Logger LOG = LoggerFactory.getLogger(EtradeAccountListServlet.class);

    @ApiOperation(
            httpMethod = "GET",
            value = "Get a list of E*Trade accounts.",
            nickname = "accounts")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Account list returned by E*Trade."),
            @ApiResponse(code = 400, message = "If OAuth tokens have not been initialized."),
            @ApiResponse(code = 500, message = "If unable to return account list.")})
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        long timeStartedMillis = System.currentTimeMillis();
        doEtradeGet(request, response);
        LOG.info("Completed request in {}ms", System.currentTimeMillis() - timeStartedMillis);    }

    @Override
    public void handleException(HttpServletResponse response, Exception e) throws IOException {
        LOG.error("Failed to fetch accountList", e);
        response.sendError(500, "Unable to fetch accounts");
    }

    @Override
    public Message newMessage(HttpServletRequest request) {
        Message accountListMessage = new Message();
        accountListMessage.setRequiresOauth(true);
        accountListMessage.setHttpMethod("GET");
        accountListMessage.setUrl(API.getAccountListUrl());
        return accountListMessage;
    }
}
