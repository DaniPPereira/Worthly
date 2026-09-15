import type { ReactNode } from "react";
import { PublicSplit } from "@/components/brand/PublicSplit";

export default function PublicLayout({ children }: { children: ReactNode }) {
  return <PublicSplit>{children}</PublicSplit>;
}
