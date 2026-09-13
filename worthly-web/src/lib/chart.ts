export function linePath(values: number[], width: number, height: number, pad: number): string {
  if (values.length === 0) {
    return "";
  }
  if (values.length === 1) {
    const y = height / 2;
    return `M0 ${y.toFixed(1)} L${width} ${y.toFixed(1)}`;
  }
  const min = Math.min(...values);
  const max = Math.max(...values);
  const span = max - min || 1;
  return values
    .map((value, index) => {
      const x = (width / (values.length - 1)) * index;
      const y = height - pad - ((value - min) / span) * (height - pad * 2);
      return `${index === 0 ? "M" : "L"}${x.toFixed(1)} ${y.toFixed(1)}`;
    })
    .join(" ");
}

export function areaPath(values: number[], width: number, height: number, pad: number): string {
  const line = linePath(values, width, height, pad);
  if (!line) {
    return "";
  }
  return `${line} L${width} ${height} L0 ${height} Z`;
}

export function toChartNumber(amount: string): number {
  const negative = amount.trim().startsWith("-");
  const unsigned = negative ? amount.trim().slice(1) : amount.trim();
  const [integer = "0", fraction = ""] = unsigned.split(".");
  const cents = Number.parseInt(`${integer}${(fraction + "00").slice(0, 2)}`, 10);
  return negative ? -cents : cents;
}
