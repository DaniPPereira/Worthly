export function transactionsHref(opts: {
  from?: string | null;
  to?: string | null;
  categoryId?: string | null;
  economicType?: string | null;
  open?: string | null;
  q?: string | null;
} = {}): string {
  const params = new URLSearchParams();
  if (opts.from) {
    params.set("from", opts.from);
  }
  if (opts.to) {
    params.set("to", opts.to);
  }
  if (opts.categoryId) {
    params.set("categoryId", opts.categoryId);
  }
  if (opts.economicType) {
    params.set("economicType", opts.economicType);
  }
  if (opts.q) {
    params.set("q", opts.q);
  }
  if (opts.open) {
    params.set("open", opts.open);
  }
  const query = params.toString();
  return query ? `/transactions?${query}` : "/transactions";
}
