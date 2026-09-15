export type ChartScale = "zero" | "minmax";

type Point = { x: number; y: number };

function cleanValues(values: number[]): number[] {
  return values.map((value) => (Number.isFinite(value) ? value : 0));
}

function chartPoints(
  values: number[],
  width: number,
  height: number,
  pad: number,
  scale: ChartScale,
): { points: Point[]; baselineY: number } {
  const clean = cleanValues(values);
  if (clean.length === 0) {
    return { points: [], baselineY: height - pad };
  }
  const min = scale === "zero" ? Math.min(0, ...clean) : Math.min(...clean);
  const max = scale === "zero" ? Math.max(0, ...clean) : Math.max(...clean);
  const span = max - min || 1;
  const innerHeight = height - pad * 2;
  const yFor = (value: number) => pad + (1 - (value - min) / span) * innerHeight;
  const n = clean.length;
  const points = clean.map((value, index) => ({
    x: n === 1 ? width / 2 : ((index + 0.5) / n) * width,
    y: yFor(value),
  }));
  return { points, baselineY: yFor(scale === "zero" ? 0 : min) };
}

export function linePath(
  values: number[],
  width: number,
  height: number,
  pad: number,
  scale: ChartScale = "minmax",
): string {
  const { points } = chartPoints(values, width, height, pad, scale);
  if (points.length === 0) {
    return "";
  }
  if (points.length === 1) {
    const y = points[0].y.toFixed(1);
    return `M0 ${y} L${width} ${y}`;
  }
  return points
    .map((point, index) => `${index === 0 ? "M" : "L"}${point.x.toFixed(1)} ${point.y.toFixed(1)}`)
    .join(" ");
}

export function areaPath(
  values: number[],
  width: number,
  height: number,
  pad: number,
  scale: ChartScale = "zero",
): string {
  const { points, baselineY } = chartPoints(values, width, height, pad, scale);
  if (points.length === 0) {
    return "";
  }
  const line = linePath(values, width, height, pad, scale);
  const first = points[0];
  const last = points[points.length - 1];
  const base = baselineY.toFixed(1);
  return `${line} L${last.x.toFixed(1)} ${base} L${first.x.toFixed(1)} ${base} Z`;
}

export function toChartNumber(amount: string): number {
  const negative = amount.trim().startsWith("-");
  const unsigned = negative ? amount.trim().slice(1) : amount.trim();
  const [integer = "0", fraction = ""] = unsigned.split(".");
  const cents = Number.parseInt(`${integer}${(fraction + "00").slice(0, 2)}`, 10);
  if (!Number.isFinite(cents)) {
    return 0;
  }
  return negative ? -cents : cents;
}
