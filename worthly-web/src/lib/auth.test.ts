import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { oauthBasicAuthorization } from "./oauth-basic.ts";

describe("oauthBasicAuthorization", () => {
  it("form-encodes plus signs so Spring does not treat them as spaces", () => {
    const header = oauthBasicAuthorization("worthly-web", "abc+123/x=");
    const encoded = header.slice("Basic ".length);
    const decoded = Buffer.from(encoded, "base64").toString("utf8");
    assert.equal(decoded, "worthly-web:abc%2B123%2Fx%3D");
  });
});
