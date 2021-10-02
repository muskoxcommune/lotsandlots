package io.lotsandlots.web.servlet;

import io.lotsandlots.etrade.EtradeRestTemplateFactory;
import io.lotsandlots.etrade.ApiConfig;
import io.lotsandlots.etrade.Message;
import io.lotsandlots.etrade.oauth.SecurityContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class EtradeAccountListServlet extends HttpServlet implements EtradeServlet {

    private static final ApiConfig API = EtradeRestTemplateFactory.getClient().getApiConfig();
    private static final Logger LOG = LoggerFactory.getLogger(EtradeAccountListServlet.class);

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SecurityContext securityContext = EtradeRestTemplateFactory.getClient().getSecurityContext();
        if (!securityContext.isInitialized()) {
            response.sendError(400, "Please go to /etrade/authorize");
            return;
        }

        Message accountListMessage = new Message();
        accountListMessage.setRequiresOauth(true);
        accountListMessage.setHttpMethod("GET");
        accountListMessage.setUrl(API.getBaseUrl() + API.getAcctListUri());
        try {
            setOauthHeader(securityContext, accountListMessage);
            ResponseEntity<String> accountListResponse = EtradeRestTemplateFactory
                    .getClient()
                    .newCustomRestTemplate()
                    .execute(accountListMessage, String.class);
            String responseBody = accountListResponse.getBody();
            if (StringUtils.isBlank(responseBody)) {
                throw new RuntimeException("Etrade API returned empty response");
            }
            response.getWriter().print(responseBody);
        } catch (Exception e) {
            LOG.error("Failed to fetch accountList", e);
            response.sendError(500, "Unable to fetch accounts");
        }
    }
}
