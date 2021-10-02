package io.lotsandlots.etrade.oauth.model;

public class OAuthConfig {

    private String consumerKey;
    private String sharedSecret;
    private String requestTokenUrl;
    private String authorizeUrl;
    private String accessTokenUrl;
    private Signer signatureMethod;
    private String requestTokenHttpMethod = "GET";
    private String accessTokenHttpMethod = "GET";

    public String getConsumerKey() {
        return consumerKey;
    }
    public void setConsumerKey(String consumerKey) {
        this.consumerKey = consumerKey;
    }

    public String getSharedSecret() {
        return sharedSecret;
    }
    public void setSharedSecret(String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }

    public String getRequestTokenUrl() {
        return requestTokenUrl;
    }
    public void setRequestTokenUrl(String requestTokenUrl) {
        this.requestTokenUrl = requestTokenUrl;
    }

    public String getAuthorizeUrl() {
        return authorizeUrl;
    }
    public void setAuthorizeUrl(String authorizeUrl) {
        this.authorizeUrl = authorizeUrl;
    }

    public String getAccessTokenUrl() {
        return accessTokenUrl;
    }
    public void setAccessTokenUrl(String accessTokenUrl) {
        this.accessTokenUrl = accessTokenUrl;
    }

    public Signer getSignatureMethod() {
        return signatureMethod;
    }
    public void setSignatureMethod(Signer signatureMethod) {
        this.signatureMethod = signatureMethod;
    }

    public String getRequestTokenHttpMethod() {
        return requestTokenHttpMethod;
    }
    public void setRequestTokenHttpMethod(String requestTokenHttpMethod) {
        this.requestTokenHttpMethod = requestTokenHttpMethod;
    }

    public String getAccessTokenHttpMethod() {
        return accessTokenHttpMethod;
    }
    public void setAccessTokenHttpMethod(String accessTokenHttpMethod) {
        this.accessTokenHttpMethod = accessTokenHttpMethod;
    }
}
