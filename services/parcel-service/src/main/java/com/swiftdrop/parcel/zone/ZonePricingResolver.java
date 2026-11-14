package com.swiftdrop.parcel.zone;

import com.swiftdrop.parcel.dto.QuoteRequest;

/** Finds the zones and pricing rule for a quote. {@link #NONE} keeps the default rates (unit tests, no database). */
@FunctionalInterface
public interface ZonePricingResolver {

    ZonePricingResolver NONE = request -> ZonePricing.none();

    /**
     * @throws ZoneValidationException (400) when a location is outside every active zone or a zone id is unknown
     */
    ZonePricing resolve(QuoteRequest request);
}
