import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { addAmounts, compareAmountDesc, formatAmount, formatRate, formatSignedAmount, groupByCurrency, weightPercent } from "./money.ts";

describe("money formatting", () => {
  it("formats EUR with Portuguese grouping and never uses a float", () => {
    assert.equal(formatAmount("12430.21", "EUR"), "12.430,21 €");
    assert.equal(formatAmount("-8.4", "EUR"), "-8,40 €");
    assert.equal(formatSignedAmount("3150.00", "EUR"), "+3.150,00 €");
  });

  it("keeps a non-EUR code instead of converting", () => {
    assert.equal(formatAmount("100.00", "USD"), "100,00 USD");
  });

  it("masks values in privacy mode", () => {
    assert.equal(formatAmount("12430.21", "EUR", true), "••••••");
    assert.equal(formatRate("40.8", true), "•••");
  });

  it("groups rows by currency without mixing them", () => {
    const grouped = groupByCurrency(
      [
        { currency: "EUR", amount: "10.00" },
        { currency: "USD", amount: "4.00" },
        { currency: "EUR", amount: "2.50" },
      ],
      (row) => row.currency,
    );
    assert.equal(grouped.get("EUR")?.length, 2);
    assert.equal(grouped.get("USD")?.length, 1);
  });

  it("adds decimal strings in cents without a float", () => {
    assert.equal(addAmounts("0.00", "112.05"), "112.05");
    assert.equal(addAmounts("40.00", "12.50"), "52.50");
    assert.equal(addAmounts("-8.40", "10.00"), "1.60");
  });

  it("computes same-currency weights with integer cents", () => {
    assert.equal(weightPercent("50.00", "200.00"), "25,0%");
    assert.equal(compareAmountDesc("20.00", "5.00"), -1);
  });
});
