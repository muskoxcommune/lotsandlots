package io.lotsandlots.etrade.oauth.model;

import java.util.HashMap;

public class SecurityContext extends HashMap<String, OAuthToken> {

    Resource resources;
    private boolean initialized;

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    public Resource getResource() {
        return resources;
    }
    public void setResource(Resource resources) {
        this.resources = resources;
    }

    public OAuthToken getToken() {
        return super.get("TOKEN");
    }
}
