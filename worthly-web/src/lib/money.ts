export type Money = {
  amount: string;
  currency: string;
};

const HIDDEN = "••••••";

function parseParts(amount: string): { negative: boolean; integer: string; fraction: string } {
  const trimmed = amount.trim();
  const negative = trimmed.startsWith("-");
  const unsigned = negative ? trimmed.slice(1) : trimmed;
  const [integer = "0", fraction = ""] = unsigned.split(".");
  const digits = integer.replace(/^0+(?=\d)/, "") || "0";
  const cents = (fraction + "00").slice(0, 2);
  return { negative, integer: digits, fraction: cents };
}

function groupThousands(integer: string): string {
  return integer.replace(/\B(?=(\d{3})+(?!\d))/g, ".");
}

export function formatAmount(amount: string, currency: string, privacy = false): string {
  if (privacy) {
    return HIDDEN;
  }
  const { negative, integer, fraction } = parseParts(amount);
  const body = `${groupThousands(integer)},${fraction}`;
  const signed = negative ? `-${body}` : body;
  if (currency === "EUR") {
    return `${signed} €`;
  }
  return `${signed} ${currency}`;
}

export function formatSignedAmount(amount: string, currency: string, privacy = false): string {
  if (privacy) {
    return HIDDEN;
  }
  const { negative, integer, fraction } = parseParts(amount);
  const body = `${groupThousands(integer)},${fraction}`;
  const prefix = negative ? "−" : "+";
  if (currency === "EUR") {
    return `${prefix}${body} €`;
  }
  return `${prefix}${body} ${currency}`;
}

export function formatRate(value: string | null, privacy = false): string {
  if (privacy) {
    return "•••";
  }
  if (value == null || value === "") {
    return "—";
  }
  const { negative, integer, fraction } = parseParts(value);
  return `${negative ? "-" : ""}${groupThousands(integer)},${fraction}%`;
}

export function isNegative(amount: string): boolean {
  return amount.trim().startsWith("-") && amount.trim() !== "-0" && amount.trim() !== "-0.00";
}

export function absAmount(amount: string): string {
  return amount.trim().startsWith("-") ? amount.trim().slice(1) : amount.trim();
}

export function addAmounts(left: string, right: string): string {
  const a = parseParts(left);
  const b = parseParts(right);
  const leftMag = BigInt(a.integer) * 100n + BigInt(a.fraction);
  const rightMag = BigInt(b.integer) * 100n + BigInt(b.fraction);
  const sum = (a.negative ? -leftMag : leftMag) + (b.negative ? -rightMag : rightMag);
  const negative = sum < 0n;
  const mag = negative ? -sum : sum;
  return `${negative ? "-" : ""}${(mag / 100n).toString()}.${(mag % 100n).toString().padStart(2, "0")}`;
}

export function groupByCurrency<T>(items: T[], currencyOf: (item: T) => string): Map<string, T[]> {
  const groups = new Map<string, T[]>();
  for (const item of items) {
    const currency = currencyOf(item);
    const existing = groups.get(currency);
    if (existing) {
      existing.push(item);
    } else {
      groups.set(currency, [item]);
    }
  }
  return groups;
}

export function compareAmountDesc(left: string, right: string): number {
  const a = parseParts(left);
  const b = parseParts(right);
  const leftMag = BigInt(a.integer) * 100n + BigInt(a.fraction);
  const rightMag = BigInt(b.integer) * 100n + BigInt(b.fraction);
  if (leftMag === rightMag) {
    return 0;
  }
  return leftMag > rightMag ? -1 : 1;
}

export function weightPercent(part: string, total: string): string {
  const p = parseParts(part);
  const t = parseParts(total);
  const partMag = BigInt(p.integer) * 100n + BigInt(p.fraction);
  const totalMag = BigInt(t.integer) * 100n + BigInt(t.fraction);
  if (totalMag === 0n) {
    return "0,0%";
  }
  const bps = (partMag * 1000n) / totalMag;
  const whole = bps / 10n;
  const tenth = bps % 10n;
  return `${whole.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ".")},${tenth}%`;
}

export function barPercent(part: string, total: string): string {
  const p = parseParts(part);
  const t = parseParts(total);
  const partMag = BigInt(p.integer) * 100n + BigInt(p.fraction);
  const totalMag = BigInt(t.integer) * 100n + BigInt(t.fraction);
  if (totalMag === 0n) {
    return "0%";
  }
  const pct = (partMag * 100n) / totalMag;
  return `${pct.toString()}%`;
}
