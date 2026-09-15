import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { formatMerchantRule, merchantRuleSuggestion, splitMatchPhrases, stemMerchant } from "./merchant-rule.ts";

describe("merchant rule stems", () => {
  it("strips Portuguese bank prefixes and cities", () => {
    const hint = stemMerchant("COMPRA  1531 DECATHLON BRAGA");
    assert.deepEqual(hint, { matchValue: "decathlon", operator: "CONTAINS", display: "Decathlon", field: "MERCHANT" });
  });

  it("keeps two short words like Pingo Doce", () => {
    const hint = stemMerchant("COMPRA PINGO DOCE LISBOA");
    assert.deepEqual(hint, { matchValue: "pingo doce", operator: "CONTAINS", display: "Pingo Doce", field: "MERCHANT" });
  });

  it("keeps domain-like tokens", () => {
    const hint = stemMerchant("COMPRA ESTRANG APPLE.COM/BILL");
    assert.deepEqual(hint, { matchValue: "apple.com", operator: "CONTAINS", display: "apple.com", field: "MERCHANT" });
  });

  it("uses equals only when the merchant is already the stem", () => {
    const hint = stemMerchant("Netflix");
    assert.deepEqual(hint, { matchValue: "netflix", operator: "EQUALS", display: "Netflix", field: "MERCHANT" });
  });

  it("does not offer generic processors", () => {
    assert.equal(stemMerchant("Top-up by Revolut"), null);
    assert.equal(stemMerchant("PAYPAL"), null);
  });
});

describe("merchant rule suggestion", () => {
  it("skips Uncategorized and existing merchant rules", () => {
    assert.equal(
      merchantRuleSuggestion({
        merchant: "COMPRA DECATHLON",
        description: null,
        categoryCode: "uncategorized",
        previousCategoryId: null,
        nextCategoryId: "cat-1",
        rules: [],
      }),
      null,
    );
    assert.equal(
      merchantRuleSuggestion({
        merchant: "COMPRA DECATHLON BRAGA",
        description: null,
        categoryCode: "expense.shopping",
        previousCategoryId: null,
        nextCategoryId: "cat-1",
        rules: [{ field: "MERCHANT", operator: "CONTAINS", matchValue: "decathlon", enabled: true }],
      }),
      null,
    );
  });

  it("offers a new merchant contains rule", () => {
    const hint = merchantRuleSuggestion({
      merchant: "COMPRA  1531 DECATHLON BRAGA",
      description: null,
      categoryCode: "expense.shopping",
      previousCategoryId: null,
      nextCategoryId: "cat-1",
      rules: [],
    });
    assert.equal(hint?.matchValue, "decathlon");
    assert.equal(hint?.field, "MERCHANT");
    assert.equal(
      formatMerchantRule({ field: "DESCRIPTION", operator: "CONTAINS", matchValue: "veterinario" }, "Pets"),
      "Description contains “veterinario” → Pets",
    );
  });

  it("falls back to the bank description when the merchant is generic", () => {
    const hint = merchantRuleSuggestion({
      merchant: "PAYPAL",
      description: "DEBITO DIRETO VETERINARIO CENTRAL",
      categoryCode: "expense.other",
      previousCategoryId: null,
      nextCategoryId: "cat-1",
      rules: [],
    });
    assert.equal(hint?.matchValue, "veterinario");
    assert.equal(hint?.field, "DESCRIPTION");
  });

  it("splits description phrases", () => {
    assert.deepEqual(splitMatchPhrases("veterinario\npetshop, Petshop"), ["veterinario", "petshop"]);
  });
});
