package io.lotsandlots.etrade;

import io.lotsandlots.etrade.oauth.OAuth1Helper;
import io.lotsandlots.etrade.oauth.SecurityContext;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;

public interface EtradeApiClient {

    default void setOauthHeader(SecurityContext securityContext, Message message)
            throws UnsupportedEncodingException, GeneralSecurityException {
        OAuth1Helper oAuth1Helper = new OAuth1Helper(securityContext, message);
        oAuth1Helper.computeOauthSignature();
        message.setOauthHeader(oAuth1Helper.getAuthorizationHeader());
    }
}
