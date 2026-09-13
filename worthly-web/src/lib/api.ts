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
  return JSON.parse(text) as T;
}

function redirectIfUnauthorized(status: number): void {
  if (status === 401 && !window.location.pathname.startsWith("/login")) {
    window.location.href = "/login";
  }
}

export async function apiGet<T>(path: string): Promise<T> {
  const response = await fetch(`/api/worthly${path}`, { credentials: "same-origin" });
  redirectIfUnauthorized(response.status);
  if (!response.ok) {
    throw new ApiError(response.status, "request_failed");
  }
  const body = await parseBody<T>(response);
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
  redirectIfUnauthorized(response.status);
  if (!response.ok) {
    throw new ApiError(response.status, "request_failed");
  }
  return parseBody<T>(response);
}

export async function downloadCsv(path: string, filename: string): Promise<void> {
  const response = await fetch(`/api/worthly${path}`, { credentials: "same-origin" });
  redirectIfUnauthorized(response.status);
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
