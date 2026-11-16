package com.saumya.pos.platform;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Global platform configuration rules. Secrets are never stored or returned: secret keys hold only a reference
 * (env:NAME or vault:path) that the runtime resolves, and every read is masked. Mock providers are refused in production.
 */
public final class ConfigurationPolicy {
    public enum Kind { TEXT, SECRET_REF, PROVIDER }
    /** Keys for the SaaS operator's own subscription-payment merchant account; never shared with vendor merchant accounts. */
    public enum Key {
        SUBSCRIPTION_PAYMENT_PROVIDER("platform.subscription.payment.provider",Kind.PROVIDER),
        SUBSCRIPTION_MERCHANT_ID("platform.subscription.payment.merchant_id",Kind.TEXT),
        SUBSCRIPTION_API_SECRET_REF("platform.subscription.payment.api_secret_ref",Kind.SECRET_REF),
        SUBSCRIPTION_WEBHOOK_SECRET_REF("platform.subscription.payment.webhook_secret_ref",Kind.SECRET_REF),
        NOTIFICATION_SENDER("platform.notification.sender",Kind.TEXT);
        private final String id;private final Kind kind;
        Key(String id,Kind kind){this.id=id;this.kind=kind;}
        public String id(){return id;} public Kind kind(){return kind;}
        public static Optional<Key> find(String id){return Arrays.stream(values()).filter(key->key.id.equals(id)).findFirst();}
    }
    public static final class Rejected extends RuntimeException {public Rejected(String message){super(message);}}
    private static final Pattern SECRET_REF=Pattern.compile("^(env|vault):[A-Za-z0-9_./-]{1,120}$");
    private static final Pattern PROVIDER=Pattern.compile("^[A-Z][A-Z0-9_]{1,39}$");
    private ConfigurationPolicy(){}

    public static boolean secret(Key key){return key.kind==Kind.SECRET_REF;}
    public static String validate(Key key,String value,boolean production){
        if(value==null||value.isBlank())throw new Rejected("A value is required");
        String v=value.strip();
        if(v.length()>200)throw new Rejected("Value is too long");
        switch(key.kind){
            case SECRET_REF -> {if(!SECRET_REF.matcher(v).matches())throw new Rejected("Secrets are never stored here; supply a reference such as env:NAME or vault:path");}
            case PROVIDER -> {
                if(!PROVIDER.matcher(v).matches())throw new Rejected("Provider must be an upper-case identifier");
                if(production&&(v.equals("MOCK")||v.startsWith("MOCK_")))throw new Rejected("Mock providers are forbidden in production");
            }
            case TEXT -> {if(v.chars().anyMatch(Character::isISOControl))throw new Rejected("Value contains control characters");}
        }
        return v;
    }
    /** Value safe to return from any API or audit record. */
    public static String mask(Key key,String value){
        if(value==null)return null;
        if(key.kind!=Kind.SECRET_REF)return value;
        int colon=value.indexOf(':');return (colon>0?value.substring(0,colon+1):"")+"********";
    }
}
