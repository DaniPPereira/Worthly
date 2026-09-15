import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { monthDateRange, monthKeyInZone, monthKeysThrough, previousMonthKeys, shiftMonthKey } from "./period.ts";

describe("reporting periods", () => {
  it("reads the calendar month in the owner timezone", () => {
    const at = new Date("2026-09-14T00:30:00Z");
    assert.equal(monthKeyInZone("Europe/Lisbon", at), "2026-09");
    assert.equal(monthKeyInZone("UTC", at), "2026-09");
  });

  it("returns six month keys ending at the current reporting month", () => {
    const keys = previousMonthKeys("UTC", 6, new Date("2026-09-14T12:00:00Z"));
    assert.deepEqual(keys, ["2026-04", "2026-05", "2026-06", "2026-07", "2026-08", "2026-09"]);
  });

  it("includes the last day of February", () => {
    assert.deepEqual(monthDateRange("2026-02"), { from: "2026-02-01", to: "2026-02-28" });
  });

  it("shifts a month key across year boundaries", () => {
    assert.equal(shiftMonthKey("2026-01", -1), "2025-12");
    assert.equal(shiftMonthKey("2025-12", 1), "2026-01");
  });

  it("builds a window ending at a selected month", () => {
    assert.deepEqual(monthKeysThrough("2026-03", 4), ["2025-12", "2026-01", "2026-02", "2026-03"]);
  });
});
