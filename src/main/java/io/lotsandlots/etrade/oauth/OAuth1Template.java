package io.lotsandlots.etrade.oauth;

import io.lotsandlots.etrade.Message;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.net.URLCodec;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class OAuth1Template {

    private static final BitSet WWW_FORM_URL_SAFE = new BitSet(256);
    /*
     * Characters in the unreserved character set MUST NOT be encoded
     */
    static {
        // alpha characters
        for (int i = 'a'; i <= 'z'; i++) {
            WWW_FORM_URL_SAFE.set(i);
        }
        for (int i = 'A'; i <= 'Z'; i++) {
            WWW_FORM_URL_SAFE.set(i);
        }
        // numeric characters
        for (int i = '0'; i <= '9'; i++) {
            WWW_FORM_URL_SAFE.set(i);
        }
        // special chars
        WWW_FORM_URL_SAFE.set('-');
        WWW_FORM_URL_SAFE.set('_');
        WWW_FORM_URL_SAFE.set('.');
        WWW_FORM_URL_SAFE.set('~');

    }

    private static final Logger LOG = LoggerFactory.getLogger(OAuth1Template.class);

    private static final SecureRandom secureRand = new SecureRandom();

    private OAuthSigner oauthSigner = null;

    private String callback = "oob";

    /** Required nonce value. Should be computed. */
    private String oauthNonce;

    /** Realm. */
    private String realm = "";

    /** Signature. Required but normally computed. */
    private String signature;

    /**
     * Name of the signature method used by the client to sign the request. Required, but normally
     * computed using
     */
    private String signatureMethod;

    /** Required timestamp value. Should be computed. */
    private String timestamp;

    private final SecurityContext context;

    private final Message message;

    private BitSet unreserved = null;

    public OAuth1Template(SecurityContext context, Message message) {
        this.message = message;
        this.context = context;
        this.oauthSigner = getSigner();

    }

    /**
     *
     * @param buf - Buffer to  append the key=value seperated by coma
     * @param key - Key name to append to buffer
     * @param value - value to append to buffer
     * Format : OAuth realm="",oauth_signature="y%2Fkoia4FeOmALwM9Zl94pAKxjiw%3D",oauth_nonce="MTE2OTUzMzA0NDM4MDg2NzQwMw%3D%3D",
     *          oauth_signature_method="HMAC-SHA1",oauth_consumer_key="xxxxxxxxxxxxxxxxxxx",
     *          oauth_token="yyyyyyyyyyyyyyyyyyyyyyyyyyyy",oauth_timestamp="1557366622"
     */
    private void append(StringBuilder buf, final String key, final String value){
        buf.append(key).append("=\"").append(value).append("\",");
    }

    /*
     * The timestamp value MUST be a positive integer and MUST be equal or greater than the timestamp used in previous requests
     */
    public String computeNonce() {
        long generatedNo = secureRand.nextLong();
        return (oauthNonce = new String(Base64.encodeBase64((String.valueOf(generatedNo)).getBytes())));
    }

    public String computeTimestamp() {
        return (timestamp = Long.toString(System.currentTimeMillis() / 1000));
    }

    /**
     * @param method - Http Method
     * @param uri - Url for api request
     * @throws UnsupportedEncodingException
     * @throws GeneralSecurityException
     */
    public void computeOauthSignature(String method, String url)
            throws UnsupportedEncodingException, GeneralSecurityException{
        computeOauthSignature(method, url, "");
    }

    /**
     * Method to generate the oauth signature string.  oauth_signature, string made up of several HTTP request elements in a single string.
     * @param method - Http Method
     * @param uri - Url for api request
     * @param queryString - Query String used in request
     * @throws UnsupportedEncodingException
     * @throws GeneralSecurityException
     */
    public void computeOauthSignature(String method, String uri, String queryString)
            throws UnsupportedEncodingException, GeneralSecurityException {
        this.oauthNonce = computeNonce();
        this.signatureMethod = getSigner().getSignatureMethod();
        this.timestamp = computeTimestamp();

        Map<String, String[]> requestMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        requestMap.put("oauth_consumer_key",new String[] { context.getOAuthConfig().getConsumerKey() });
        requestMap.put("oauth_timestamp", new String[] { this.timestamp });
        requestMap.put("oauth_nonce", new String[] { this.oauthNonce});
        requestMap.put("oauth_signature_method",new String[] { this.signatureMethod });

        OAuthToken oauthtoken = this.context.getToken();

        if( oauthtoken != null ){
            requestMap.put("oauth_token", new String[]{oauthtoken.getOauthToken()});
            LOG.debug(" verifier code :"+ message.getVerifierCode());
            if( StringUtils.isNotBlank(message.getVerifierCode())){
                requestMap.put("oauth_verifier", new String[]{encode(message.getVerifierCode())});
            }
        }else{
            requestMap.put("oauth_callback", new String[] {getCallback()});
            requestMap.put("oauth_version", new String[] {"1.0"});
        }

        method = method.toUpperCase();

        LOG.debug("The Query String  "+ queryString);

        if( StringUtils.isNotBlank(queryString)) {
            Map<String,String[]> qParamMap = getQueryStringMap(queryString);
            if( !qParamMap.isEmpty()){
                requestMap.putAll(qParamMap);
            }
        }

        String encodedParams = encode(getNormalizedParams(requestMap));
        LOG.debug("Normalized string after encoding " + encodedParams);
        String encodedUri = encode(message.getUrl());
        String baseString = method + "&" + encodedUri + "&" + encodedParams;
        LOG.debug("signature base string " + baseString );
        this.signature = oauthSigner.computeSignature(baseString, context);
        LOG.debug("signature " + signature);
    }

    /**
     *
     * @param value to encode
     * @return encoded string
     */
    public static String encode(String value) {
        if (value == null) {
            return "";
        }
        try {
            return new String(URLCodec.encodeUrl(WWW_FORM_URL_SAFE, value.getBytes("UTF-8")), "US-ASCII");
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
    public static String decode(String value)  {
        if (value == null) {
            return "";
        }
        try {
            return new String(URLCodec.decodeUrl(value.getBytes("US-ASCII")), "UTF-8");
        } catch (UnsupportedEncodingException | DecoderException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * It is a single string and separated generally by a comma and named Authorization.
     * @return String - oauth header string for oauth/api request
     */
    public String getAuthorizationHeader() {
        MultiValueMap<String, String> requestMap = getHeaderMap();
        StringBuilder buf = new StringBuilder("");
        LOG.debug("getAuthorizationHeader : " + context.isInitialized()
                + " isRequiresOauth " + message.isRequiresOauth());
        if (context.isInitialized() || message.isRequiresOauth()) {
            buf.append("OAuth ");
            //append(buf,"realm", "");z\

            for (Map.Entry<String, List<String>> e : requestMap.entrySet()) {
                append(buf, encode(e.getKey()), encode(e.getValue().get(0)));
            }
            return buf.substring(0, buf.length() - 1);
        } else {
            message.setQueryString("consumerKey="+context.getOAuthConfig().getConsumerKey());
        }
        return "";
    }

    public String getCallback() {
        return callback;
    }
    public void setCallback(String callback) {
        this.callback = callback;
    }

    /**
     *
     * The OAuth header is a part of the signed request, it contains the oauth_signature
     * and oauth_signature_method parameters and their values.
     *
     * @return String - oauth header map for  oaut/api request
     */
    public MultiValueMap<String, String> getHeaderMap() {

        MultiValueMap<String, String> requestMap = new LinkedMultiValueMap<>();
        if ( context.isInitialized() || message.isRequiresOauth()) {
            requestMap.add("oauth_consumer_key", context.getOAuthConfig().getConsumerKey());
            requestMap.add("oauth_timestamp",  this.timestamp );
            requestMap.add("oauth_nonce",this.oauthNonce);
            requestMap.add("oauth_signature_method",context.getOAuthConfig().getSignatureMethod().getValue());
            requestMap.add("oauth_signature", this.signature);
            if (context != null && context.getToken() !=  null) {
                OAuthToken oAuthToken = context.getToken();
                requestMap.add("oauth_token", /*encode(*/oAuthToken.getOauthToken()/*)*/);
                if (StringUtils.isNotBlank(message.getVerifierCode())) {
                    requestMap.add("oauth_verifier", /*encode(*/message.getVerifierCode()/*)*/);
                }
            } else {
                requestMap.add("oauth_callback", getCallback());
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
            requestMap.add("consumerKey", context.getOAuthConfig().getConsumerKey());

        }
        return requestMap;
    }

    /**
     * The request parameters are collected, sorted and concatenated into a normalized string
     * Parameters in the OAuth HTTP Authorization header excluding the realm parameter.
     * HTTP GET parameters added to the URLs in the query part (as defined by [RFC3986] section 3).
     * @param paramMap - Request parameter for normalization process.
     * @return String - Normalized string
     */
    private String getNormalizedParams(Map<String,String[]> paramMap){
        StringBuilder combinedParams = new StringBuilder();
        TreeMap<String,String[]> sortedMap = new TreeMap<String,String[]>();
        for (String key:paramMap.keySet()) {
            //the value can be null, in which case we do not want any associated array here
            String[] encodedValues = (paramMap.get(key)!=null) ? new String[paramMap.get(key).length] : new String[0];
            //encode keys, and sort the array
            for (int i=0; i< encodedValues.length; i++) {
                encodedValues[i] = encode(paramMap.get(key)[i]);
            }
            Arrays.sort(encodedValues);
            sortedMap.put(encode(key), encodedValues);
        }
        //now we generate a string for the map in key=value&key1=value1 format by concatenating the values
        StringBuilder normalizedRequest = new StringBuilder();
        for (String key: sortedMap.keySet()) {
            //we need to handle the case if the value is null
            if (sortedMap.get(key)==null || sortedMap.get(key).length==0) {
                normalizedRequest.append(key + "=&");
            }
            for (String value: sortedMap.get(key)) {
                //this for loop will not execute if the value is null or empty
                normalizedRequest.append(key + "=" + value + "&");
            }
        }
        return normalizedRequest.toString().substring(0,normalizedRequest.length()-1);

    }

    public String getOauthNonce() {
        return oauthNonce;
    }
    public void setOauthNonce(String oauthNonce) {
        this.oauthNonce = oauthNonce;
    }

    public void setOauthSigner(OAuthSigner oauthsigner) {
        this.oauthSigner = oauthsigner;
    }

    /**
     *
     * HTTP GET parameters added to the URLs in the query part (as defined by [RFC3986] section 3).
     */
    private Map<String, String[]> getQueryStringMap(String queryString){
        Map<String,String[]> queryParamMap = new HashMap<>();
        if (queryString != null && queryString.length() > 0) {
            for (String keyValue : queryString.split("&")) {
                String[] p = keyValue.split("=");
                queryParamMap.put(p[0],new String[] {p[1]});
            }
        }
        return queryParamMap;
    }

    public String getRealm() {
        return realm;
    }
    public void setRealm(String realm) {
        this.realm = realm;
    }

    public String getSignature() {
        return signature;
    }
    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getSignatureMethod() {
        return signatureMethod;
    }
    public void setSignatureMethod(String signatureMethod) {
        this.signatureMethod = signatureMethod;
    }

    /**
     * The timestamp value MUST be a positive integer and MUST be equal or greater than the
     * timestamp used in previous requests.
     *
     * @return timestamp string
     */
    public String getTimestamp() {
        return timestamp;
    }

    /**
     * @return OAuthSigner - Returns HMAC-SHA1 implementation based on the property defined under oauth property file
     */
    private OAuthSigner getSigner() {
        OAuthSigner signer = null;
        if (context.getOAuthConfig().getSignatureMethod() == Signer.HMAC_SHA1) {
            signer = new HmacSha1Signer();
        }
        return signer;
    }
}
