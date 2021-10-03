package io.lotsandlots.etrade.oauth;

public class SecurityContext {

    private boolean initialized;
    private OAuthConfig oAuthConfig;
    private OAuthToken token;

    public OAuthConfig getOAuthConfig() {
        return oAuthConfig;
    }
    public void setOAuthConfig(OAuthConfig oAuthConfig) {
        this.oAuthConfig = oAuthConfig;
    }

    public boolean isInitialized() {
        return initialized;
    }
    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    public OAuthToken getToken() {
        return token;
    }
    public void setToken(OAuthToken token) {
        this.token = token;
    }
}
