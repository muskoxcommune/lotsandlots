package io.lotsandlots.etrade.oauth;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class HmacSha1Signer implements OAuthSigner {

    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";
    private static final Logger LOG = LoggerFactory.getLogger(HmacSha1Signer.class);

    @Override
    public String getSignatureMethod() {
        return "HMAC-SHA1";
    }

    @Override
    public String computeSignature(String signatureBaseString, SecurityContext context)
            throws GeneralSecurityException {
        LOG.debug("HmacSha1Signer - signatureBaseString " + signatureBaseString);

        String key = "";
        OAuthToken token = context.getToken();
        if (token != null) {
            key = StringUtils.isEmpty(token.getOauthTokenSecret())
                    ? context.getOAuthConfig().getSharedSecret() + "&"
                    : context.getOAuthConfig().getSharedSecret() + "&" + OAuth1Template.encode(token.getOauthTokenSecret());
        } else {
            key = context.getOAuthConfig().getSharedSecret() + "&";
        }

        SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(),HMAC_SHA1_ALGORITHM);
        // Get a HmacSHA1 Mac instance and initialize with the signing key
        Mac mac = null;
        try {
            mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(e);
        }
        try {
            mac.init(signingKey);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException(e);
        }
        // Compute the hmac on the signatureBaseString
        byte[] raw = mac.doFinal(signatureBaseString.getBytes());
        String result = new String(Base64.encodeBase64(raw));
        LOG.debug("Computed signature from HMAC " + result);
        return result;
    }
}
