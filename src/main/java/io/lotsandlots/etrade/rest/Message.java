package io.lotsandlots.etrade.rest;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

public class Message {

    private String body;
    private String contentType;
    private final MultiValueMap<String, String> headerMap = new LinkedMultiValueMap<>();
    private String httpMethod;
    private String oauthHeader;
    private String queryString;
    private boolean requiresOauth = false;
    private String url;
    private String verifierCode;

    public String getBody() {
        return body;
    }
    public void setBody(String body) {
        this.body = body;
    }

    public String getContentType() {
        return contentType;
    }
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getHttpMethod() {
        return httpMethod;
    }
    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getOauthHeader() {
        return oauthHeader;
    }
    public void setOAuthHeader(String oauthHeader) {
        this.oauthHeader = oauthHeader;
    }

    public String getQueryString() {
        return queryString;
    }
    public void setQueryString(String queryString) {
        this.queryString = queryString;
    }

    public boolean isRequiresOauth() {
        return requiresOauth;
    }
    public void setRequiresOauth(boolean requiresOauth) {
        this.requiresOauth = requiresOauth;
    }

    public String getUrl() {
        return url;
    }
    public void setUrl(String url) {
        this.url = url;
    }

    public String getVerifierCode() {
        return verifierCode;
    }
    public void setVerifierCode(String verifierCode) {
        this.verifierCode = verifierCode;
    }

    @Override
    public String toString() {
        return "Message{"
                + "body: " + body + ", "
                + "headerMap: " + headerMap + ", "
                + "httpMethod: " + httpMethod + ", "
                + "oauthHeader: " + oauthHeader + ", "
                + "queryString: " + queryString + ", "
                + "url: " + url + ", "
                + "verifierCode: " + verifierCode
                + "}";
    }
}
