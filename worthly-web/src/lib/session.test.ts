import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  CSRF_COOKIE,
  SESSION_COOKIE,
  SESSION_IDLE_SECONDS,
  csrfCookieOptions,
  sessionCookieOptions,
  sessionStillValid,
} from "./session.ts";
import { clearAuthCookies } from "./cookies.ts";
import { NextResponse } from "next/server";

describe("BFF session cookies", () => {
  it("uses HttpOnly web_session and a non-HttpOnly CSRF cookie", () => {
    assert.equal(SESSION_COOKIE, "web_session");
    assert.equal(sessionCookieOptions.httpOnly, true);
    assert.equal(sessionCookieOptions.sameSite, "lax");
    assert.equal(sessionCookieOptions.path, "/");
    assert.equal(sessionCookieOptions.maxAge, SESSION_IDLE_SECONDS);
    assert.equal(SESSION_IDLE_SECONDS, 30 * 60);
    assert.equal(sessionCookieOptions.path, "/");
    assert.equal(CSRF_COOKIE, "worthly_csrf");
    assert.equal(csrfCookieOptions.httpOnly, false);
  });

  it("rejects sessions older than 12 hours even if the idle cookie is still present", () => {
    const fresh = {
      accessToken: "a",
      refreshToken: "r",
      accessExpiresAt: Date.now() + 60_000,
      sessionId: "s",
      issuedAt: Date.now(),
    };
    assert.equal(sessionStillValid(fresh), true);
    assert.equal(sessionStillValid({ ...fresh, issuedAt: Date.now() - 12 * 60 * 60 * 1000 - 1 }), false);
    assert.equal(sessionStillValid({ ...fresh, issuedAt: 0 }), false);
  });

  it("expires web_session, oauth_flow and CSRF cookies on logout", () => {
    const response = clearAuthCookies(new NextResponse());
    const names = response.cookies.getAll().map((cookie) => cookie.name);
    assert.deepEqual(names.sort(), ["oauth_flow", "web_session", "worthly_csrf"].sort());
    for (const cookie of response.cookies.getAll()) {
      assert.equal(cookie.value, "");
      assert.equal(cookie.maxAge, 0);
    }
  });
});
