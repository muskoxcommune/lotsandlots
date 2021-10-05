package io.lotsandlots.web.servlet;

import io.lotsandlots.etrade.EtradeApiClient;
import io.lotsandlots.etrade.EtradeRestTemplateFactory;
import io.lotsandlots.etrade.Message;
import io.lotsandlots.etrade.oauth.SecurityContext;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public interface EtradeApiServlet extends EtradeApiClient {

    default void doEtradeGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SecurityContext securityContext = EtradeRestTemplateFactory.getClient().getSecurityContext();
        if (!securityContext.isInitialized()) {
            response.sendError(400, "Please go to /etrade/authorize");
            return;
        }
        try {
            Message message = newMessage(request);
            setOauthHeader(securityContext, message);
            ResponseEntity<String> responseEntity = EtradeRestTemplateFactory
                    .getClient()
                    .newCustomRestTemplate()
                    .execute(message, String.class);
            String responseBody = responseEntity.getBody();
            if (StringUtils.isBlank(responseBody)) {
                throw new RuntimeException("Empty response");
            }
            response.getWriter().print(responseBody);
        } catch (Exception e) {
            handleException(response, e);
        }
    }

    void handleException(HttpServletResponse response, Exception e) throws IOException;

    Message newMessage(HttpServletRequest request) throws InvalidParameterException;

    class InvalidParameterException extends Exception {

        InvalidParameterException(String message) {
            super(message);
        }
    }
}
