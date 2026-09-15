import { createHash } from "node:crypto";
import { CompactEncrypt, compactDecrypt } from "jose";
import { config } from "./config";

export const SESSION_COOKIE = "web_session";
export const CSRF_COOKIE = "worthly_csrf";
export const OAUTH_FLOW_COOKIE = "oauth_flow";

export const SESSION_IDLE_SECONDS = 30 * 60;
export const SESSION_ABSOLUTE_MS = 12 * 60 * 60 * 1000;

export type WebSession = {
  accessToken: string;
  refreshToken: string;
  accessExpiresAt: number;
  sessionId: string;
  issuedAt: number;
};

export function sessionStillValid(session: WebSession): boolean {
  const issuedAt = session.issuedAt || 0;
  return issuedAt > 0 && Date.now() - issuedAt < SESSION_ABSOLUTE_MS;
}

export type OauthFlow = {
  state: string;
  verifier: string;
};

function secretKey(): Uint8Array {
  return createHash("sha256").update(config.sessionSecret).digest();
}

export async function encryptPayload(payload: object): Promise<string> {
  return new CompactEncrypt(new TextEncoder().encode(JSON.stringify(payload)))
    .setProtectedHeader({ alg: "dir", enc: "A256GCM" })
    .encrypt(secretKey());
}

export async function decryptPayload<T>(token: string): Promise<T> {
  const { plaintext } = await compactDecrypt(token, secretKey());
  return JSON.parse(new TextDecoder().decode(plaintext)) as T;
}

export const sessionCookieOptions = {
  httpOnly: true,
  secure: process.env.NODE_ENV === "production",
  sameSite: "lax" as const,
  path: "/",
  maxAge: SESSION_IDLE_SECONDS,
};

export const csrfCookieOptions = {
  httpOnly: false,
  secure: process.env.NODE_ENV === "production",
  sameSite: "lax" as const,
  path: "/",
  maxAge: 60 * 60 * 24 * 14,
};

export const oauthFlowCookieOptions = {
  httpOnly: true,
  secure: process.env.NODE_ENV === "production",
  sameSite: "lax" as const,
  path: "/",
  maxAge: 10 * 60,
};
