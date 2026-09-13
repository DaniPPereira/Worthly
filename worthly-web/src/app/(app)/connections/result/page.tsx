import { redirect } from "next/navigation";

export default async function ConnectionResultPage({
  searchParams,
}: {
  searchParams: Promise<{ status?: string; connectionId?: string; code?: string }>;
}) {
  const params = await searchParams;
  const query = new URLSearchParams();
  if (params.status) {
    query.set("status", params.status);
  }
  if (params.code) {
    query.set("code", params.code);
  }
  redirect(`/connections?${query.toString()}`);
}
