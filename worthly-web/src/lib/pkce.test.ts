import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { encodeQuery, pkceChallenge, pkceVerifier } from "./pkce.ts";

describe("PKCE", () => {
  it("creates an S256 challenge distinct from the verifier", () => {
    const verifier = pkceVerifier();
    const challenge = pkceChallenge(verifier);
    assert.ok(verifier.length >= 43);
    assert.notEqual(verifier, challenge);
    assert.equal(pkceChallenge(verifier), challenge);
  });

  it("encodes OAuth scope spaces as %20 not plus", () => {
    const query = encodeQuery({
      scope: "openid profile worthly.read worthly.write",
    });
    assert.equal(query, "scope=openid%20profile%20worthly.read%20worthly.write");
  });
});
