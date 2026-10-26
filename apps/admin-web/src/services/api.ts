const API_BASE = (import.meta.env.VITE_API_BASE_URL as string) || "https://e-rickshaw-z32z.onrender.com/api/v1";

export async function fetchWithAuth(endpoint: string, options: RequestInit = {}) {
  const token = localStorage.getItem("admin_token") || "mock_admin_token";
  const headers = {
    "Content-Type": "application/json",
    "Authorization": `Bearer ${token}`,
    ...(options.headers || {})
  };

  try {
    const res = await fetch(`${API_BASE}${endpoint}`, { ...options, headers });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return await res.json();
  } catch (err) {
    console.warn(`Backend API not reached for ${endpoint}, using staging state.`, err);
    return null;
  }
}
