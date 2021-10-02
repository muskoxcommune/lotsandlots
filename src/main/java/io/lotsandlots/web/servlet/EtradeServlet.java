package io.lotsandlots.web.servlet;

import io.lotsandlots.etrade.oauth.OAuth1Template;
import io.lotsandlots.etrade.oauth.model.Message;
import io.lotsandlots.etrade.oauth.model.SecurityContext;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;

public interface EtradeServlet {

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
