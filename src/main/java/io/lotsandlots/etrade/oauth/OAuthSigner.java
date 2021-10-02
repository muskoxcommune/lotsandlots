package io.lotsandlots.etrade.oauth;

import java.security.GeneralSecurityException;

public interface OAuthSigner {

    String getSignatureMethod();

    String computeSignature(String signatureBaseString, SecurityContext context) throws GeneralSecurityException;
}