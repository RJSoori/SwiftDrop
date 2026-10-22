/**
 * The only way the web app talks to the backend. Calls go to /api/v1/... on our own origin; next.config.ts
 * proxies them to the gateway, so the HttpOnly auth cookies are first-party and never touched by JS.
 * On a 401 it refreshes the session once (shared by concurrent requests) and retries the request.
 */

export type FieldErrors = Record<string, string>;

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly fieldErrors: FieldErrors = {},
  ) {
    super(message);
    this.name = "ApiError";
  }
}

type ProblemDetail = {
  title?: string;
  detail?: string;
  errors?: { field: string; message: string }[];
};

type ApiInit = Omit<RequestInit, "body"> & { json?: unknown };

// Endpoints where a 401 means "bad credentials" or "session over", not "access token expired".
const NO_REFRESH = new Set(["/auth/login", "/auth/signup", "/auth/signup/merchant", "/auth/refresh"]);

let refreshing: Promise<boolean> | null = null;

/** One refresh at a time: parallel 401s wait for the same call instead of each rotating the token. */
function refreshSession(): Promise<boolean> {
  refreshing ??= fetch("/api/v1/auth/refresh", { method: "POST", credentials: "same-origin" })
    .then((res) => res.ok, () => false)
    .finally(() => {
      refreshing = null;
    });
  return refreshing;
}

function send(path: string, { json, headers, ...init }: ApiInit): Promise<Response> {
  return fetch(`/api/v1${path}`, {
    ...init,
    credentials: "same-origin",
    headers: json === undefined ? headers : { "Content-Type": "application/json", ...headers },
    body: json === undefined ? undefined : JSON.stringify(json),
  });
}

async function toError(res: Response): Promise<ApiError> {
  let problem: ProblemDetail = {};
  try {
    problem = await res.json();
  } catch {
    // not JSON (e.g. gateway 401/429 with an empty body)
  }
  const fieldErrors: FieldErrors = {};
  for (const e of problem.errors ?? []) fieldErrors[e.field] ??= e.message;
  const fallback = res.status === 429 ? "Too many attempts. Wait a minute and try again." : `Request failed (${res.status})`;
  return new ApiError(res.status, problem.detail ?? problem.title ?? fallback, fieldErrors);
}

export async function api<T = void>(path: string, init: ApiInit = {}): Promise<T> {
  let res = await send(path, init);
  if (res.status === 401 && !NO_REFRESH.has(path) && (await refreshSession())) {
    res = await send(path, init);
  }
  if (!res.ok) throw await toError(res);
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export type Role = "CUSTOMER" | "MERCHANT" | "COURIER" | "DISPATCHER" | "ADMIN";
export type MerchantStatus = "PENDING" | "APPROVED" | "REJECTED" | "SUSPENDED";

export type User = {
  id: string;
  name: string;
  email: string;
  roles: Role[];
  merchantStatus?: MerchantStatus;
};

export type MerchantApplication = {
  id: string;
  email: string;
  fullName: string;
  businessName: string;
  registrationNo: string;
  status: MerchantStatus;
  createdAt: string;
};
