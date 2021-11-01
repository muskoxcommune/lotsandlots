package io.lotsandlots.etrade.oauth;

import io.lotsandlots.etrade.rest.Message;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.net.URLCodec;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class OAuth1Helper {

    private static final String CALLBACK = "oob";
    private static final Logger LOG = LoggerFactory.getLogger(OAuth1Helper.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final BitSet WWW_FORM_URL_SAFE = new BitSet(256);
    // Characters in the unreserved character set MUST NOT be encoded
    static {
        // Alpha characters
        for (int i = 'a'; i <= 'z'; i++) {
            WWW_FORM_URL_SAFE.set(i);
        }
        for (int i = 'A'; i <= 'Z'; i++) {
            WWW_FORM_URL_SAFE.set(i);
        }
        // Numeric characters
        for (int i = '0'; i <= '9'; i++) {
            WWW_FORM_URL_SAFE.set(i);
        }
        // Special chars
        WWW_FORM_URL_SAFE.set('-');
        WWW_FORM_URL_SAFE.set('_');
        WWW_FORM_URL_SAFE.set('.');
        WWW_FORM_URL_SAFE.set('~');
    }

    private final Message message;
    private final SecurityContext securityContext;

    private String oauthNonce;
    private OAuthSigner oauthSigner = null;
    private String signature;
    private String timestamp;

    public OAuth1Helper(SecurityContext securityContext, Message message) {
        this.message = message;
        this.oauthSigner = new HmacSha1Signer();
        this.securityContext = securityContext;
    }

    public void computeOAuthSignature() throws UnsupportedEncodingException, GeneralSecurityException {
        oauthNonce = new String(Base64.encodeBase64(String.valueOf(RANDOM.nextLong()).getBytes()));
        timestamp = Long.toString(System.currentTimeMillis() / 1000);

        Map<String, String[]> params = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        params.put("oauth_consumer_key", new String[] {securityContext.getOAuthConfig().getConsumerKey()});
        params.put("oauth_timestamp", new String[] {timestamp});
        params.put("oauth_nonce", new String[] {oauthNonce});
        params.put("oauth_signature_method", new String[] {oauthSigner.getSignatureMethod()});

        OAuthToken oauthtoken = securityContext.getToken();
        if (oauthtoken != null){
            params.put("oauth_token", new String[]{oauthtoken.getOauthToken()});
            if (StringUtils.isNotBlank(message.getVerifierCode())) {
                params.put("oauth_verifier", new String[]{encode(message.getVerifierCode())});
            }
        } else {
            params.put("oauth_callback", new String[] {CALLBACK});
            params.put("oauth_version", new String[] {"1.0"});
        }

        if (StringUtils.isNotBlank(message.getQueryString())) {
            Map<String, String[]> queryStringMap = new HashMap<>();
            if (!StringUtils.isBlank(message.getQueryString())) {
                for (String keyValue : message.getQueryString().split("&")) {
                    String[] p = keyValue.split("=");
                    queryStringMap.put(p[0], new String[] {p[1]});
                }
            }
            if (!queryStringMap.isEmpty()){
                params.putAll(queryStringMap);
            }
        }

        TreeMap<String, String[]> sortedParams = new TreeMap<>();
        for (String key : params.keySet()) {
            // The value can be null, in which case we do not want an array
            String[] encodedValues = (params.get(key) != null) ? new String[params.get(key).length]
                    : new String[0];
            // Encode keys, and sort the array
            for (int i=0; i< encodedValues.length; i++) {
                encodedValues[i] = encode(params.get(key)[i]);
            }
            Arrays.sort(encodedValues);
            sortedParams.put(encode(key), encodedValues);
        }
        // Generate a string in key=value&key1=value1 format
        StringBuilder normalizedParams = new StringBuilder();
        for (String key: sortedParams.keySet()) {
            if (sortedParams.get(key) == null || sortedParams.get(key).length==0) {
                normalizedParams.append(key).append("=&");
            }
            for (String value: sortedParams.get(key)) {
                normalizedParams.append(key).append("=").append(value).append("&");
            }
        }

        String baseString =
                message.getHttpMethod().toUpperCase()
                + "&" + encode(message.getUrl())
                + "&" + encode(normalizedParams.substring(0, normalizedParams.length()-1));
        signature = oauthSigner.computeSignature(baseString, securityContext);
    }

    /**
     *
     * @param value to encode
     * @return encoded string
     */
    public static String encode(String value) throws UnsupportedEncodingException {
        if (value == null) {
            return "";
        }
        return new String(URLCodec.encodeUrl(
                WWW_FORM_URL_SAFE, value.getBytes(StandardCharsets.UTF_8)), StandardCharsets.US_ASCII);
    }

    public void setAuthorizationHeader() throws UnsupportedEncodingException {
        MultiValueMap<String, String> requestMap = new LinkedMultiValueMap<>();
        if (securityContext.isInitialized() || message.isRequiresOauth()) {
            requestMap.add("oauth_consumer_key", securityContext.getOAuthConfig().getConsumerKey());
            requestMap.add("oauth_timestamp",  timestamp );
            requestMap.add("oauth_nonce", oauthNonce);
            requestMap.add("oauth_signature_method", oauthSigner.getSignatureMethod());
            requestMap.add("oauth_signature", signature);
            if (securityContext.getToken() != null) {
                OAuthToken oAuthToken = securityContext.getToken();
                requestMap.add("oauth_token", oAuthToken.getOauthToken());
                if (StringUtils.isNotBlank(message.getVerifierCode())) {
                    requestMap.add("oauth_verifier", message.getVerifierCode());
                }
            } else {
                requestMap.add("oauth_callback", CALLBACK);
                requestMap.add("oauth_version", "1.0");
            }
            if ( StringUtils.isNotBlank(message.getQueryString())) {
                for (String keyValue : message.getQueryString().split("&")) {
                    String[] p = keyValue.split("=");
                    requestMap.add(p[0], p[1]);
                }
            }
        } else {
            //in case of quotes api call, delayed quotes will be returned
            requestMap.add("consumerKey", securityContext.getOAuthConfig().getConsumerKey());
        }

        // Format:
        // OAuth realm="",oauth_signature="xxxxxxxxxxxxxxxxxxx",oauth_nonce="xxxxxxxxxxxxxxxxxxx%3D%3D",
        // oauth_signature_method="HMAC-SHA1",oauth_consumer_key="xxxxxxxxxxxxxxxxxxx",
        // oauth_token="xxxxxxxxxxxxxxxxxxx",oauth_timestamp="1557366622"
        StringBuilder stringBuilder = new StringBuilder();
        if (securityContext.isInitialized() || message.isRequiresOauth()) {
            stringBuilder.append("OAuth ");
            for (Map.Entry<String, List<String>> e : requestMap.entrySet()) {
                stringBuilder
                        .append(encode(e.getKey()))
                        .append("=\"")
                        .append(encode(e.getValue().get(0)))
                        .append("\",");
            }
            message.setOAuthHeader(stringBuilder.substring(0, stringBuilder.length() - 1));
        } else {
            message.setQueryString("consumerKey=" + securityContext.getOAuthConfig().getConsumerKey());
        }
    }

    interface OAuthSigner {

        String getSignatureMethod();

        String computeSignature(String signatureBaseString, SecurityContext context)
                throws GeneralSecurityException, UnsupportedEncodingException;
    }

    static class HmacSha1Signer implements OAuthSigner {

        private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";

        @Override
        public String getSignatureMethod() {
            return "HMAC-SHA1";
        }

        @Override
        public String computeSignature(String signatureBaseString, SecurityContext context)
                throws GeneralSecurityException, UnsupportedEncodingException {
            String key;
            OAuthToken token = context.getToken();
            if (token != null) {
                key = StringUtils.isEmpty(token.getOauthTokenSecret())
                        ? context.getOAuthConfig().getSharedSecret() + "&"
                        : context.getOAuthConfig().getSharedSecret() + "&" + OAuth1Helper.encode(token.getOauthTokenSecret());
            } else {
                key = context.getOAuthConfig().getSharedSecret() + "&";
            }
            SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), HMAC_SHA1_ALGORITHM);

            Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
            mac.init(signingKey);
            return new String(Base64.encodeBase64(mac.doFinal(signatureBaseString.getBytes())));
        }
    }
}
