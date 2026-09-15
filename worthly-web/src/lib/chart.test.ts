import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { areaPath, linePath, toChartNumber } from "./chart.ts";

describe("chart paths", () => {
  it("places each month in the centre of its band", () => {
    const path = linePath([0, 50, 100], 300, 100, 10, "zero");
    assert.match(path, /^M50\.0 /);
    assert.match(path, / L150\.0 /);
    assert.match(path, / L250\.0 /);
  });

  it("keeps zero on the baseline instead of stretching the smallest month to the floor", () => {
    const path = linePath([10, 100], 200, 100, 10, "zero");
    const firstY = Number(path.split(" ")[1]);
    const lastY = Number(path.split(" ")[3]);
    assert.ok(firstY > lastY);
    assert.ok(firstY < 90);
    assert.equal(lastY, 10);
  });

  it("closes the fill on the last month, not the SVG corner", () => {
    const area = areaPath([10, 100], 200, 100, 10, "zero");
    assert.equal(area.includes(" L200 100 "), false);
    assert.match(area, / L150\.0 90\.0 L50\.0 90\.0 Z$/);
  });

  it("parses signed amounts into cents", () => {
    assert.equal(toChartNumber("164.88"), 16488);
    assert.equal(toChartNumber("-12.5"), -1250);
  });
});
