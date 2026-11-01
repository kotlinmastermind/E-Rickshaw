export const API_BASE = (import.meta.env.VITE_API_BASE_URL as string) || "https://e-rickshaw-z32z.onrender.com/api/v1";

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
  return `${API_BASE.replace(/\/$/, "")}/${endpoint.replace(/^\//, "")}`;
}

export async function requestApi<T = any>(endpoint: string, options: RequestInit = {}, authenticated = true): Promise<T> {
  let token = localStorage.getItem("admin_token") || "";

  // Auto-provision Superadmin session if no token is stored yet
  if (authenticated && !token && !endpoint.includes("/auth/")) {
    try {
      const demoRes = await fetch(resolveUrl("/api/v1/auth/admin/demo-session"), {
        method: "POST",
        headers: { "Content-Type": "application/json" }
      });
      if (demoRes.ok) {
        const demoData = await demoRes.json();
        if (demoData.access_token) {
          token = demoData.access_token;
          localStorage.setItem("admin_token", token);
          if (demoData.user) {
            localStorage.setItem("admin_user", JSON.stringify(demoData.user));
          }
        }
      }
    } catch (e) {
      console.warn("Auto-provision demo session fallback:", e);
    }
  }

  const headers = {
    "Content-Type": "application/json",
    ...(authenticated && token ? { "Authorization": `Bearer ${token}` } : {}),
    ...(options.headers || {})
  };
  const res = await fetch(resolveUrl(endpoint), { ...options, headers });
  const contentType = res.headers.get("content-type") || "";
  const body = contentType.includes("application/json") ? await res.json().catch(() => null) : await res.text().catch(() => "");
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
