package com.swiftdrop.auth.credentials;

import com.swiftdrop.auth.domain.MerchantAccount;

import java.util.UUID;

/**
 * The single place that decides what "merchantId" means outside auth-service (introspection response, the
 * {@value #CLAIM} JWT claim). Today it is the merchant's user id, so it always equals the token's {@code sub}
 * and matches what merchant-service sees from a dashboard login (CurrentUser.id()).
 * If M3/M6 agree on a different cross-service id (e.g. merchant_accounts.id), change {@link #of} only.
 */
public final class MerchantIdentity {

    public static final String CLAIM = "merchantId";

    private MerchantIdentity() {
    }

    public static UUID of(MerchantAccount merchant) {
        return merchant.getUser().getId();
    }
}
