package io.lotsandlots.etrade.oauth;

public enum Signer {

    HMAC_SHA1("HMAC-SHA1");

    private final String value;

    Signer(String v) {
        this.value = v;
    }

    public static Signer getSigner(String v) {
        Signer p = Signer.HMAC_SHA1;
        for(Signer s : Signer.values()) {
            if( s.getValue().equals(v)) {
                p = s;
            }
        }
        return p;
    }

    public String getValue() {
        return value;
    }
}
