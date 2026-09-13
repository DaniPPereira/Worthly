import { createHash, randomBytes } from "node:crypto";

export function pkceVerifier(): string {
  return randomBytes(32).toString("base64url");
}

export function pkceChallenge(verifier: string): string {
  return createHash("sha256").update(verifier).digest("base64url");
}

export function randomState(): string {
  return randomBytes(24).toString("base64url");
}

export function randomCsrf(): string {
  return randomBytes(32).toString("base64url");
}

export function encodeQuery(params: Record<string, string>): string {
  return Object.entries(params)
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    .join("&");
}
