package io.lotsandlots.web.servlet;

import io.lotsandlots.etrade.EtradeRestTemplateFactory;
import io.lotsandlots.etrade.oauth.OAuth1Template;
import io.lotsandlots.etrade.Message;
import io.lotsandlots.etrade.oauth.SecurityContext;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;

public interface EtradeServlet {

    default void doEtradeGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SecurityContext securityContext = EtradeRestTemplateFactory.getClient().getSecurityContext();
        if (!securityContext.isInitialized()) {
            response.sendError(400, "Please go to /etrade/authorize");
            return;
        }

        Message message = newMessage(request);
        try {
            setOauthHeader(securityContext, message);
            ResponseEntity<String> accountListResponse = EtradeRestTemplateFactory
                    .getClient()
                    .newCustomRestTemplate()
                    .execute(message, String.class);
            String responseBody = accountListResponse.getBody();
            if (StringUtils.isBlank(responseBody)) {
                throw new RuntimeException("Etrade API returned empty response");
            }
            response.getWriter().print(responseBody);
        } catch (Exception e) {
            handleException(response, e);
        }
    }

    void handleException(HttpServletResponse response, Exception e) throws IOException;

    Message newMessage(HttpServletRequest request);

    default void setOauthHeader(SecurityContext securityContext, Message message)
            throws UnsupportedEncodingException, GeneralSecurityException {
        OAuth1Template oAuth1Template = new OAuth1Template(securityContext, message);
        oAuth1Template.computeOauthSignature(
                message.getHttpMethod(),
                message.getUrl(),
                message.getQueryString());
        message.setOauthHeader(oAuth1Template.getAuthorizationHeader());
    }
}
