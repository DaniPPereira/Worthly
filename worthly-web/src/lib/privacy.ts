export const PRIVACY_STORAGE_KEY = "worthly.privacy";

export function readPrivacy(): boolean {
  try {
    return window.localStorage.getItem(PRIVACY_STORAGE_KEY) === "1";
  } catch {
    return false;
  }
}

export function writePrivacy(hidden: boolean): void {
  try {
    window.localStorage.setItem(PRIVACY_STORAGE_KEY, hidden ? "1" : "0");
  } catch {
    /* ignore quota / private mode */
  }
}
