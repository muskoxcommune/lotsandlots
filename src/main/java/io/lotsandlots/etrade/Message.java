package io.lotsandlots.etrade;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

public class Message {

    private final MultiValueMap<String, String> headerMap = new LinkedMultiValueMap<>();

    private String body;
    private String httpMethod;
    private String oauthHeader;
    private String queryString;
    private boolean requiresOauth = false;
    private String url;
    private String verifierCode;

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

    public String getQueryString() {
        return queryString;
    }
    public void setQueryString(String queryString) {
        this.queryString = queryString;
    }

    public MultiValueMap<String, String> getHeaderMap() {
        return headerMap;
    }

    public String getHttpMethod() {
        return httpMethod;
    }
    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getBody() {
        return body;
    }
    public void setBody(String body) {
        this.body = body;
    }

    public String getOauthHeader() {
        return oauthHeader;
    }
    public void setOauthHeader(String oauthHeader) {
        this.oauthHeader = oauthHeader;
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
