import { readFileSync } from "node:fs";

function readOptionalFile(path: string | undefined): string | undefined {
  if (!path) {
    return undefined;
  }
  try {
    return readFileSync(path, "utf8").trim();
  } catch {
    return undefined;
  }
}

export const config = {
  apiUrl: process.env.WORTHLY_API_URL ?? "http://localhost:8080",
  issuer: process.env.WORTHLY_ISSUER ?? process.env.WORTHLY_API_URL ?? "http://localhost:8080",
  origin: process.env.WORTHLY_WEB_ORIGIN ?? "http://localhost:3000",
  clientId: process.env.WORTHLY_WEB_CLIENT_ID ?? "worthly-web",
  clientSecret:
    process.env.WORTHLY_WEB_CLIENT_SECRET ??
    readOptionalFile(process.env.WORTHLY_WEB_CLIENT_SECRET_FILE) ??
    "",
  sessionSecret:
    process.env.WORTHLY_SESSION_SECRET ??
    readOptionalFile(process.env.WORTHLY_SESSION_SECRET_FILE) ??
    "",
};

export function requireSecrets(): void {
  if (!config.clientSecret) {
    throw new Error("WORTHLY_WEB_CLIENT_SECRET or WORTHLY_WEB_CLIENT_SECRET_FILE is required");
  }
  if (config.sessionSecret.length < 32) {
    throw new Error("WORTHLY_SESSION_SECRET must be at least 32 characters");
  }
}
