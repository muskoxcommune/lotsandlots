package io.lotsandlots.etrade.oauth;

import java.io.Serializable;

public class OAuthToken implements Serializable {

    private final String oauthToken;
    private final String oauthTokenSecret;
    private TokenType tokenType;

    public OAuthToken(String oauthToken, String oauthTokenSecret, TokenType tokenType) {
        this.oauthToken = oauthToken;
        this.oauthTokenSecret = oauthTokenSecret;
        this.tokenType = tokenType;
    }

    public String getOauthToken() {
        return oauthToken;
    }

    public String getOauthTokenSecret() {
        return oauthTokenSecret;
    }

    public TokenType getTokenType() {
        return tokenType;
    }

    public enum TokenType {
        ACCESS,
        REQUEST
    }
}
