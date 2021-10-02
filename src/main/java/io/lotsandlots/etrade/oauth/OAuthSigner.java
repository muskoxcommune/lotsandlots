package io.lotsandlots.etrade.oauth;

import io.lotsandlots.etrade.oauth.model.SecurityContext;

import java.security.GeneralSecurityException;

public interface OAuthSigner {

    String getSignatureMethod();

    String computeSignature(String signatureBaseString, SecurityContext context) throws GeneralSecurityException;
}