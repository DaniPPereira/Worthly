import type { Metadata } from "next";
import type { ReactNode } from "react";
import { IBM_Plex_Mono, Instrument_Serif, Public_Sans } from "next/font/google";
import "./globals.css";

const publicSans = Public_Sans({
  subsets: ["latin"],
  variable: "--font-sans",
  display: "swap",
});

const instrumentSerif = Instrument_Serif({
  subsets: ["latin"],
  weight: "400",
  variable: "--font-serif",
  display: "swap",
});

const ibmPlexMono = IBM_Plex_Mono({
  subsets: ["latin"],
  weight: ["400", "500", "600"],
  variable: "--font-mono",
  display: "swap",
});

export const metadata: Metadata = {
  title: "Worthly",
  description: "Private personal finance",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body className={`${publicSans.variable} ${instrumentSerif.variable} ${ibmPlexMono.variable}`}>
        {children}
      </body>
    </html>
  );
}
