import type { Category } from "@/lib/types";

export function parentCategories(categories: Category[]): Category[] {
  return categories.filter(
    (item) => item.system && item.code && item.code !== "uncategorized" && !item.code.startsWith("transfer."),
  );
}

export function defaultParentId(categories: Category[]): string {
  const parents = parentCategories(categories);
  return parents.find((item) => item.code === "expense.other")?.id ?? parents[0]?.id ?? "";
}

export function customCategories(categories: Category[]): Category[] {
  return categories.filter((item) => !item.system);
}
