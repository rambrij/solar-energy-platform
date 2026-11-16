package com.saumya.pos.platform;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import static com.saumya.pos.platform.SubscriptionLifecycle.State.*;

/** Subscription state machine (SRS 13.4). Distinct from the tenant lifecycle; a subscription never clears a manual tenant suspension. */
public final class SubscriptionLifecycle {
    public enum State { PENDING, ACTIVE, PAST_DUE, GRACE_PERIOD, SUSPENDED, CANCEL_AT_PERIOD_END, CANCELLED, EXPIRED }
    private static final Map<State,Set<State>> ALLOWED=new EnumMap<>(State.class);
    static {
        ALLOWED.put(PENDING,Set.of(ACTIVE,PAST_DUE));
        ALLOWED.put(ACTIVE,Set.of(PAST_DUE,CANCELLED,CANCEL_AT_PERIOD_END,EXPIRED));
        ALLOWED.put(PAST_DUE,Set.of(GRACE_PERIOD,ACTIVE));
        ALLOWED.put(GRACE_PERIOD,Set.of(ACTIVE,SUSPENDED));
        ALLOWED.put(SUSPENDED,Set.of(ACTIVE,CANCELLED));
        ALLOWED.put(CANCEL_AT_PERIOD_END,Set.of(CANCELLED));
        ALLOWED.put(CANCELLED,Set.of());
        ALLOWED.put(EXPIRED,Set.of());
    }
    private SubscriptionLifecycle(){}
    public static boolean canTransition(State from,State to){return from!=null&&to!=null&&ALLOWED.get(from).contains(to);}
    public static Set<State> next(State from){return ALLOWED.get(from);}
    public static boolean terminal(State state){return ALLOWED.get(state).isEmpty();}
    /** States in which the subscription entitlement blocks vendor writes (SRS 13.4); grace keeps normal access by default. */
    public static boolean blocksVendorWrites(State state){return state==SUSPENDED||state==CANCELLED||state==EXPIRED;}
    /** Only one subscription per tenant may be effective at a time (SUB-004). */
    public static boolean effective(State state){return !terminal(state);}
}
