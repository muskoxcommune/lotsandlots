package io.lotsandlots.etrade.oauth;

import java.util.HashMap;

public class SecurityContext extends HashMap<String, OAuthToken> {

    private boolean initialized;
    private OAuthConfig oAuthConfig;

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    public OAuthConfig getOAuthConfig() {
        return oAuthConfig;
    }
    public void setOAuthConfig(OAuthConfig oAuthConfig) {
        this.oAuthConfig = oAuthConfig;
    }

    public OAuthToken getToken() {
        return super.get("TOKEN");
    }
}
