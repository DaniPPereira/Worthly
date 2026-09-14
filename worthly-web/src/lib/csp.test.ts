import assert from "node:assert/strict";
import test from "node:test";
import { contentSecurityPolicy } from "./csp.ts";

test("development CSP allows Next.js refresh eval and inline bootstrap", () => {
  const policy = contentSecurityPolicy("n0nce", true);
  assert.match(policy, /script-src 'self' 'unsafe-inline' 'unsafe-eval'/);
  assert.doesNotMatch(policy, /nonce-n0nce/);
  assert.match(policy, /connect-src 'self' ws: wss:/);
});

test("production CSP uses a nonce and does not allow eval", () => {
  const policy = contentSecurityPolicy("abc123", false);
  assert.match(policy, /script-src 'self' 'nonce-abc123' 'strict-dynamic'/);
  assert.doesNotMatch(policy, /unsafe-eval/);
  assert.doesNotMatch(policy, /script-src[^;]*unsafe-inline/);
  assert.match(policy, /frame-ancestors 'none'/);
});
