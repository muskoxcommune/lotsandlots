package io.lotsandlots.etrade.oauth;

import java.io.Serializable;

public class OAuthToken implements Serializable {

    private final String oauthToken;
    private final String oauthTokenSecret;

    public OAuthToken(String oauthToken, String oauthTokenSecret) {
        this.oauthToken = oauthToken;
        this.oauthTokenSecret = oauthTokenSecret;
    }

    public String getOauthToken() {
        return oauthToken;
    }

    public String getOauthTokenSecret() {
        return oauthTokenSecret;
    }
}
