export const API_BASE = (import.meta.env.VITE_API_BASE_URL as string) || "https://e-rickshaw-z32z.onrender.com/api/v1";

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) { super(message); this.status = status; }
}

function resolveUrl(endpoint: string) {
  return endpoint.startsWith("http")
    ? endpoint
    : (endpoint.startsWith("/api/v1") ? `${API_BASE.replace(/\/api\/v1\/?$/, "")}${endpoint}` : `${API_BASE}${endpoint}`);
}

export async function requestApi<T = any>(endpoint: string, options: RequestInit = {}, authenticated = true): Promise<T> {
  const token = localStorage.getItem("admin_token") || "";
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
