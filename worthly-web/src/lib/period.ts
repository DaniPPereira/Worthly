export function monthKeyInZone(timeZone: string, at: Date = new Date()): string {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric",
    month: "2-digit",
  }).formatToParts(at);
  const year = parts.find((part) => part.type === "year")?.value ?? "1970";
  const month = parts.find((part) => part.type === "month")?.value ?? "01";
  return `${year}-${month}`;
}

export function previousMonthKeys(timeZone: string, count: number, at: Date = new Date()): string[] {
  const current = monthKeyInZone(timeZone, at);
  const [yearText, monthText] = current.split("-");
  let year = Number(yearText);
  let month = Number(monthText);
  const keys: string[] = [];
  for (let i = 0; i < count; i += 1) {
    keys.unshift(`${year.toString().padStart(4, "0")}-${month.toString().padStart(2, "0")}`);
    month -= 1;
    if (month === 0) {
      month = 12;
      year -= 1;
    }
  }
  return keys;
}

export function formatMonthLabel(monthKey: string): string {
  const [year, month] = monthKey.split("-");
  const date = new Date(Date.UTC(Number(year), Number(month) - 1, 1));
  return new Intl.DateTimeFormat("en-GB", { month: "short" }).format(date);
}

export function formatMonthTitle(monthKey: string): string {
  const [year, month] = monthKey.split("-");
  const date = new Date(Date.UTC(Number(year), Number(month) - 1, 1));
  return new Intl.DateTimeFormat("en-GB", { month: "long", year: "numeric" }).format(date);
}

export function monthKeysThrough(endMonthKey: string, count: number): string[] {
  const keys: string[] = [];
  let key = endMonthKey;
  for (let i = 0; i < count; i += 1) {
    keys.unshift(key);
    key = shiftMonthKey(key, -1);
  }
  return keys;
}

export function formatInstant(iso: string | null | undefined, timeZone: string): string {
  if (!iso) {
    return "Never";
  }
  return new Intl.DateTimeFormat("en-GB", {
    timeZone,
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(iso));
}

export function formatDay(iso: string, timeZone: string): string {
  return new Intl.DateTimeFormat("en-GB", {
    timeZone,
    day: "numeric",
    month: "short",
  }).format(new Date(iso));
}

export function monthDateRange(monthKey: string): { from: string; to: string } {
  const [year, month] = monthKey.split("-").map(Number);
  const last = new Date(Date.UTC(year, month, 0)).getUTCDate();
  return {
    from: `${monthKey}-01`,
    to: `${monthKey}-${last.toString().padStart(2, "0")}`,
  };
}

export function shiftMonthKey(monthKey: string, delta: number): string {
  const [year, month] = monthKey.split("-").map(Number);
  const date = new Date(Date.UTC(year, month - 1 + delta, 1));
  return `${date.getUTCFullYear().toString().padStart(4, "0")}-${(date.getUTCMonth() + 1).toString().padStart(2, "0")}`;
}
