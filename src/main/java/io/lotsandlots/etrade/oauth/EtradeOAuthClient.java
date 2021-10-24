package io.lotsandlots.etrade.oauth;

import io.lotsandlots.etrade.rest.Message;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;

public interface EtradeOAuthClient {

    default void setOAuthHeader(SecurityContext securityContext, Message message)
            throws UnsupportedEncodingException, GeneralSecurityException {
        OAuth1Helper oAuth1Helper = new OAuth1Helper(securityContext, message);
        oAuth1Helper.computeOAuthSignature();
        oAuth1Helper.setAuthorizationHeader();
    }
}
