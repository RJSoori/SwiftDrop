import { api, ApiError } from "./api";

/**
 * Courier profile in dispatch-service (M4): POST /api/v1/dispatch/couriers with
 * {userId, fullName, homeZoneId?, vehicleType}. Shape taken from M4's DispatchDtos.CourierRequest.
 *
 * Dependency: until M4's dispatch endpoints are merged into dev, this call returns 404. Creating the login in
 * auth-service must still succeed, so this never throws: it reports whether the profile was saved.
 */
export type CourierProfileRequest = {
  userId: string;
  fullName: string;
  homeZoneId?: string;
  vehicleType: string;
};

export type CourierProfileResult = { saved: true } | { saved: false; reason: string };

export const VEHICLE_TYPES = [
  { value: "MOTORBIKE", label: "Motorbike" },
  { value: "THREE_WHEELER", label: "Three-wheeler" },
  { value: "VAN", label: "Van" },
] as const;

export async function createCourierProfile(request: CourierProfileRequest): Promise<CourierProfileResult> {
  try {
    await api("/dispatch/couriers", { method: "POST", json: request });
    return { saved: true };
  } catch (error) {
    if (error instanceof ApiError && (error.status === 404 || error.status === 502 || error.status === 503)) {
      return { saved: false, reason: "the dispatch service is not available yet" };
    }
    return { saved: false, reason: error instanceof ApiError ? error.message : "the dispatch service could not be reached" };
  }
}

/** Delivery zones for the home-zone picker (M8's GET /api/v1/zones). Empty if zones are not available yet. */
export type Zone = { id: string; name: string };
