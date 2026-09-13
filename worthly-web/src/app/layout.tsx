import type { Metadata } from "next";
import type { ReactNode } from "react";

export const metadata: Metadata = {
  title: "Worthly",
  description: "Private personal finance for one owner",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body style={{ fontFamily: "sans-serif", margin: "2rem auto", maxWidth: "40rem" }}>
        {children}
      </body>
    </html>
  );
}
