import { createContext, useContext } from "react";
import type { Category, Connection, Notification, Owner } from "@/lib/types";

export type AppData = {
  owner: Owner;
  connections: Connection[];
  notifications: Notification[];
  categories: Category[];
  privacy: boolean;
  syncing: boolean;
  stamp: string;
  error: string | null;
  setPrivacy: (value: boolean) => void;
  refresh: () => Promise<void>;
  syncAll: () => Promise<void>;
};

export const AppDataContext = createContext<AppData | null>(null);

export function useAppData(): AppData {
  const value = useContext(AppDataContext);
  if (!value) {
    throw new Error("AppShell is required");
  }
  return value;
}

export function ownerLabel(owner: Owner): string {
  const name = owner.name?.trim();
  if (name) {
    return name;
  }
  const local = owner.email.split("@")[0] ?? "Owner";
  return local.charAt(0).toUpperCase() + local.slice(1);
}

export function ownerInitial(owner: Owner): string {
  return ownerLabel(owner).slice(0, 1).toUpperCase();
}

export function isBank(connection: Connection): boolean {
  return connection.kind === "BANK" || (connection.kind == null && connection.provider === "ENABLE_BANKING");
}

export function isBroker(connection: Connection): boolean {
  return connection.kind === "BROKER" || (connection.kind == null && connection.provider === "TRADING_212");
}

export function includesHoldings(connection: Connection): boolean {
  if (connection.status === "DISABLED") {
    return false;
  }
  if (connection.holdingsIncluded === true) {
    return true;
  }
  return connection.capabilities?.includes("POSITIONS") === true;
}

export function connectionLabel(connection: Connection): string {
  return connection.institutionName ?? (isBroker(connection) ? "Trading 212" : connection.provider);
}

export function statusTone(status: string): { fg: string; bg: string; bd: string; label: string; dot: string } {
  switch (status) {
    case "ACTIVE":
      return { fg: "#14654A", bg: "rgba(20,101,74,.12)", bd: "rgba(20,101,74,.35)", label: "Active", dot: "#14654A" };
    case "REAUTH_REQUIRED":
      return { fg: "#8A6412", bg: "rgba(138,100,18,.14)", bd: "rgba(138,100,18,.4)", label: "Reauth required", dot: "#C98F32" };
    case "CONFIGURATION_REQUIRED":
      return { fg: "#8A6412", bg: "rgba(138,100,18,.14)", bd: "rgba(138,100,18,.4)", label: "Configuration required", dot: "#C98F32" };
    case "ERROR":
      return { fg: "#A04A34", bg: "rgba(160,74,52,.12)", bd: "rgba(160,74,52,.35)", label: "Error", dot: "#A04A34" };
    case "DISABLED":
      return { fg: "#6B7572", bg: "rgba(19,26,25,.06)", bd: "rgba(19,26,25,.12)", label: "Disabled", dot: "#6B7572" };
    default:
      return { fg: "#6B7572", bg: "rgba(19,26,25,.06)", bd: "rgba(19,26,25,.12)", label: status, dot: "#6B7572" };
  }
}
