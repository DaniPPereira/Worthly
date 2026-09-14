export function csrfToken(): string {
  const match = document.cookie.split("; ").find((part) => part.startsWith("worthly_csrf="));
  return match ? decodeURIComponent(match.split("=")[1] ?? "") : "";
}

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function parseBody<T>(response: Response): Promise<T | null> {
  if (response.status === 204) {
    return null;
  }
  const text = await response.text();
  if (!text) {
    return null;
  }
  try {
    return JSON.parse(text) as T;
  } catch {
    return null;
  }
}

function problemCode(body: unknown): string {
  if (body && typeof body === "object") {
    const record = body as { detail?: unknown; type?: unknown };
    if (typeof record.detail === "string" && record.detail.length > 0 && !record.detail.includes(" ")) {
      return record.detail;
    }
    if (typeof record.type === "string") {
      const marker = "/problems/";
      const index = record.type.lastIndexOf(marker);
      if (index >= 0) {
        return record.type.slice(index + marker.length);
      }
    }
  }
  return "request_failed";
}

function redirectIfUnauthorized(status: number, apiPath: string): void {
  const path = window.location.pathname;
  if (status === 401 && apiPath.split("?")[0] === "/me" && !path.startsWith("/login") && path !== "/logout") {
    window.location.replace("/logout");
  }
}

export async function apiGet<T>(path: string): Promise<T> {
  const response = await fetch(`/api/worthly${path}`, { credentials: "same-origin" });
  redirectIfUnauthorized(response.status, path);
  const body = await parseBody<T>(response);
  if (!response.ok) {
    throw new ApiError(response.status, problemCode(body));
  }
  if (body == null) {
    throw new ApiError(response.status, "empty_response");
  }
  return body;
}

export async function apiSend<T>(method: string, path: string, body?: unknown): Promise<T | null> {
  const response = await fetch(`/api/worthly${path}`, {
    method,
    credentials: "same-origin",
    headers: {
      "x-csrf-token": csrfToken(),
      ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  redirectIfUnauthorized(response.status, path);
  const parsed = await parseBody<T>(response);
  if (!response.ok) {
    throw new ApiError(response.status, problemCode(parsed));
  }
  return parsed;
}

export async function downloadCsv(path: string, filename: string): Promise<void> {
  const response = await fetch(`/api/worthly${path}`, { credentials: "same-origin" });
  redirectIfUnauthorized(response.status, path);
  if (!response.ok) {
    throw new ApiError(response.status, "request_failed");
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}
