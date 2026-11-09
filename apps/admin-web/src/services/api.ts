export const API_BASE = (import.meta.env.VITE_API_BASE_URL as string) || "/api/v1";

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) { super(message); this.status = status; }
}

function resolveUrl(endpoint: string) {
  if (endpoint.startsWith("http")) return endpoint;
  const rootOrigin = API_BASE.replace(/\/api\/v1\/?$/, "");
  if (endpoint.startsWith("/health") || endpoint.startsWith("/docs") || endpoint.startsWith("/openapi.json") || endpoint.startsWith("/privacy-policy")) {
    return `${rootOrigin}${endpoint}`;
  }
  if (endpoint.startsWith("/api/v1")) {
    return `${rootOrigin}${endpoint}`;
  }
  const cleanBase = API_BASE.replace(/\/$/, "");
  const cleanEndpoint = endpoint.replace(/^\//, "");
  return cleanBase ? `${cleanBase}/${cleanEndpoint}` : `/${cleanEndpoint}`;
}

let activeRefreshPromise: Promise<string | null> | null = null;

export async function refreshAdminToken(): Promise<string | null> {
  if (activeRefreshPromise) {
    return activeRefreshPromise;
  }

  activeRefreshPromise = (async () => {
    const refreshToken = localStorage.getItem("admin_refresh_token");
    if (!refreshToken) return null;

    try {
      const res = await fetch(resolveUrl("/auth/token/refresh"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refresh_token: refreshToken })
      });

      if (res.status === 409) {
        // Concurrency grace window active: a concurrent winning request has already rotated the token.
        // Use the newly saved token from localStorage.
        return localStorage.getItem("admin_token");
      }

      if (!res.ok) {
        if (res.status === 401) {
          localStorage.removeItem("admin_token");
          localStorage.removeItem("admin_refresh_token");
        }
        return null;
      }

      const data = await res.json();
      if (data && data.access_token) {
        localStorage.setItem("admin_token", data.access_token);
        if (data.refresh_token) {
          localStorage.setItem("admin_refresh_token", data.refresh_token);
        }
        return data.access_token as string;
      }
      return null;
    } catch {
      return null;
    } finally {
      activeRefreshPromise = null;
    }
  })();

  return activeRefreshPromise;
}

export async function requestApi<T = any>(endpoint: string, options: RequestInit = {}, authenticated = true): Promise<T> {
  const token = localStorage.getItem("admin_token") || "";

  if (authenticated && !token && !endpoint.includes("/auth/")) {
    throw new ApiError(401, "Admin authentication required. Please sign in.");
  }

  const headers = {
    "Content-Type": "application/json",
    ...(authenticated && token ? { "Authorization": `Bearer ${token}` } : {}),
    ...(options.headers || {})
  };
  const res = await fetch(resolveUrl(endpoint), { ...options, headers });
  const contentType = res.headers.get("content-type") || "";
  const body = contentType.includes("application/json") ? await res.json().catch(() => null) : await res.text().catch(() => "");

  if (res.status === 401 && authenticated && !endpoint.includes("/auth/") && !(options as any)?._isRetry) {
    const refreshedToken = await refreshAdminToken();
    if (refreshedToken) {
      const retryOptions: RequestInit & { _isRetry?: boolean } = {
        ...options,
        _isRetry: true,
        headers: {
          ...(options.headers || {}),
          "Content-Type": "application/json",
          "Authorization": `Bearer ${refreshedToken}`
        }
      };
      return requestApi<T>(endpoint, retryOptions, authenticated);
    }
  }

  if (!res.ok) {
    const message = typeof body === "object" && body ? String(body.detail || body.message || `Request failed (${res.status})`) : `Request failed (${res.status})`;
    throw new ApiError(res.status, message);
  }
  return body as T;
}

export async function fetchWithAuth<T = any>(endpoint: string, options: RequestInit = {}): Promise<T | null> {
  try { return await requestApi<T>(endpoint, options, true); }
  catch (err) { console.warn(`Admin API request failed for ${endpoint}.`, err); return null; }
}

export async function downloadWithAuth(endpoint: string): Promise<Blob> {
  const token = localStorage.getItem("admin_token") || "";
  const res = await fetch(resolveUrl(endpoint), { headers: token ? { Authorization: `Bearer ${token}` } : {} });
  if (!res.ok) throw new ApiError(res.status, `Document could not be opened (${res.status}).`);
  return res.blob();
}

export async function getWebSocketTicket(): Promise<string | null> {
  const token = localStorage.getItem("admin_token");
  if (!token) return null;
  try {
    const res = await requestApi<{ ticket: string; expires_in_seconds: number }>("/ws/ticket", { method: "POST" });
    return res?.ticket || null;
  } catch {
    return null;
  }
}
