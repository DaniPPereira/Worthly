import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { CSRF_COOKIE, SESSION_COOKIE, csrfCookieOptions, sessionCookieOptions } from "./session.ts";

describe("BFF session cookies", () => {
  it("uses HttpOnly web_session and a non-HttpOnly CSRF cookie", () => {
    assert.equal(SESSION_COOKIE, "web_session");
    assert.equal(sessionCookieOptions.httpOnly, true);
    assert.equal(sessionCookieOptions.sameSite, "lax");
    assert.equal(sessionCookieOptions.path, "/");
    assert.equal(sessionCookieOptions.maxAge, 60 * 60 * 24 * 14);
    assert.equal(sessionCookieOptions.path, "/");
    assert.equal(CSRF_COOKIE, "worthly_csrf");
    assert.equal(csrfCookieOptions.httpOnly, false);
  });
});
