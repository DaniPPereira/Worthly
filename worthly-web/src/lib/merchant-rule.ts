export type MerchantRuleHint = {
  matchValue: string;
  operator: "CONTAINS" | "EQUALS";
  display: string;
  field: "MERCHANT" | "DESCRIPTION";
};

export type MerchantRuleLike = {
  field: string;
  operator: string;
  matchValue: string;
  enabled?: boolean;
};

const PREFIXES = [
  /^compra estrang\b/,
  /^compra mbw\b/,
  /^compra\b/,
  /^debito direto-?/,
  /^debito direto\b/,
  /^levantamento de numerario-?/,
  /^levantamento de numerario\b/,
  /^top-?up by\b/,
  /^top up by\b/,
];

const DROP = new Set([
  "lda",
  "sa",
  "unipessoal",
  "comercial",
  "servicos",
  "serviços",
  "portugal",
  "lisboa",
  "porto",
  "braga",
  "coimbra",
  "faro",
  "setubal",
  "aveiro",
  "pt",
  "the",
  "de",
  "do",
  "da",
  "dos",
  "das",
  "e",
  "com",
  "www",
  "bill",
  "inst",
  "mb",
  "atm",
  "sucursal",
  "loja",
  "compra",
  "estrang",
  "mbw",
]);

const GENERIC = new Set([
  "paypal",
  "revolut",
  "klarna",
  "eupago",
  "mbway",
  "mb way",
  "trading 212",
  "trading212",
  "apple pay",
  "google pay",
  "compra",
]);

export function stemMerchant(raw: string | null | undefined): MerchantRuleHint | null {
  if (!raw) {
    return null;
  }
  let text = normalizeMerchant(raw);
  if (!text) {
    return null;
  }
  let changed = true;
  while (changed) {
    changed = false;
    for (const prefix of PREFIXES) {
      const next = text.replace(prefix, "").trim();
      if (next !== text) {
        text = next;
        changed = true;
      }
    }
  }
  const tokens = text
    .split(" ")
    .map((token) => token.replace(/^\.+|\.+$/g, ""))
    .filter((token) => token.length >= 2 && !/^\d+$/.test(token) && !DROP.has(token));
  if (tokens.length === 0) {
    return null;
  }
  const matchValue =
    tokens.length >= 2 && tokens[0].length <= 6 && !tokens[0].includes(".")
      ? `${tokens[0]} ${tokens[1]}`
      : tokens[0];
  if (matchValue.length < 3 || GENERIC.has(matchValue)) {
    return null;
  }
  const normalizedSource = normalizeMerchant(raw);
  return {
    matchValue,
    operator: normalizedSource === matchValue ? "EQUALS" : "CONTAINS",
    display: titleCase(matchValue),
    field: "MERCHANT",
  };
}

export function splitMatchPhrases(raw: string): string[] {
  const seen = new Set<string>();
  const phrases: string[] = [];
  for (const part of raw.split(/[\n,;]+/)) {
    const value = part.trim().replace(/\s+/g, " ");
    if (value.length < 3 || value.length > 200) {
      continue;
    }
    const key = value.toLowerCase();
    if (seen.has(key)) {
      continue;
    }
    seen.add(key);
    phrases.push(value);
  }
  return phrases;
}

export function merchantRuleSuggestion(input: {
  merchant: string | null | undefined;
  description: string | null | undefined;
  categoryCode: string | null | undefined;
  previousCategoryId: string | null | undefined;
  nextCategoryId: string;
  rules: MerchantRuleLike[];
}): MerchantRuleHint | null {
  if (input.categoryCode === "uncategorized" || input.previousCategoryId === input.nextCategoryId) {
    return null;
  }
  const merchant = input.merchant?.trim() || "";
  const description = input.description?.trim() || "";
  const merchantHint = stemMerchant(merchant);
  if (merchantHint && !fieldAlreadyCovered(merchant, "MERCHANT", input.rules)) {
    return { ...merchantHint, field: "MERCHANT" };
  }
  const descriptionHint = stemMerchant(description);
  if (descriptionHint && !fieldAlreadyCovered(description, "DESCRIPTION", input.rules)) {
    return { ...descriptionHint, field: "DESCRIPTION" };
  }
  return null;
}

export function fieldAlreadyCovered(source: string, field: string, rules: MerchantRuleLike[]): boolean {
  const haystack = normalizeMerchant(source);
  const expected = field.toUpperCase();
  return rules.some((rule) => {
    if (rule.enabled === false || rule.field.toUpperCase() !== expected) {
      return false;
    }
    const needle = normalizeMerchant(rule.matchValue);
    if (!needle) {
      return false;
    }
    switch (rule.operator.toUpperCase()) {
      case "EQUALS":
        return haystack === needle;
      case "STARTS_WITH":
        return haystack.startsWith(needle);
      default:
        return haystack.includes(needle);
    }
  });
}

export function merchantAlreadyCovered(source: string, rules: MerchantRuleLike[]): boolean {
  return fieldAlreadyCovered(source, "MERCHANT", rules);
}

export function formatMerchantRule(rule: MerchantRuleLike, categoryLabel: string): string {
  const field = rule.field.toUpperCase() === "DESCRIPTION" ? "Description" : "Merchant";
  const operator =
    rule.operator.toUpperCase() === "EQUALS"
      ? "equals"
      : rule.operator.toUpperCase() === "STARTS_WITH"
        ? "starts with"
        : "contains";
  return `${field} ${operator} “${rule.matchValue}” → ${categoryLabel}`;
}

function normalizeMerchant(raw: string): string {
  return raw
    .normalize("NFKC")
    .toLowerCase()
    .replaceAll("www.", " ")
    .replace(/[^\p{L}\p{N}.]+/gu, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function titleCase(value: string): string {
  return value
    .split(" ")
    .map((part) => (part.includes(".") ? part : part.charAt(0).toUpperCase() + part.slice(1)))
    .join(" ");
}
