package com.swiftdrop.parcel.zone;

/**
 * The zones a quote resolved to and the rule that prices it. Any part can be null: a request without locations
 * is priced with parcel-service's default rates, as before zones existed.
 */
public record ZonePricing(ZoneRef pickupZone, ZoneRef dropoffZone, PricingRuleView rule) {

    private static final ZonePricing NONE = new ZonePricing(null, null, null);

    public static ZonePricing none() {
        return NONE;
    }
}
