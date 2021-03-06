package io.lotsandlots.web.servlet;

import com.google.json.JsonSanitizer;
import io.lotsandlots.etrade.oauth.EtradeOAuthClient;
import io.lotsandlots.etrade.rest.EtradeRestTemplateFactory;
import io.lotsandlots.etrade.rest.Message;
import io.lotsandlots.etrade.oauth.SecurityContext;
import io.lotsandlots.web.error.InvalidParameterException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public interface EtradeApiServlet extends EtradeOAuthClient {

    default void doEtradeGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SecurityContext securityContext = EtradeRestTemplateFactory.getTemplateFactory().getSecurityContext();
        if (!securityContext.isInitialized()) {
            response.sendError(400, "Please go to /etrade/authorize");
            return;
        }
        try {
            Message message = newMessage(request);
            setOAuthHeader(securityContext, message);
            ResponseEntity<String> responseEntity = EtradeRestTemplateFactory
                    .getTemplateFactory()
                    .newCustomRestTemplate()
                    .doGet(message, String.class);
            String responseBody = responseEntity.getBody();
            if (StringUtils.isBlank(responseBody)) {
                throw new RuntimeException("Empty response");
            }
            response.getWriter().print(JsonSanitizer.sanitize(responseBody));
        } catch (Exception e) {
            handleException(response, e);
        }
    }

    void handleException(HttpServletResponse response, Exception e) throws IOException;

    Message newMessage(HttpServletRequest request) throws InvalidParameterException;
}
