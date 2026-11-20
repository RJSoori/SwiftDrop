package com.swiftdrop.parcel.dto;

import com.swiftdrop.parcel.domain.Parcel;
import com.swiftdrop.parcel.domain.ParcelStatusHistory;
import java.util.List;

public record ParcelDetailResponse(
        ParcelResponse parcel,
        List<ParcelStatusHistory> history
) {
    public static ParcelDetailResponse of(Parcel parcel, List<ParcelStatusHistory> history) {
        return new ParcelDetailResponse(ParcelResponse.from(parcel), history);
    }
}
