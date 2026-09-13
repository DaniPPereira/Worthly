package com.worthly.transfers.application;

public final class TransferScore {

    private TransferScore() {}

    public static int score(long calendarDays, boolean ownedAccountHint, boolean trading212Hint, boolean purchaseHint) {
        int confidence = 60;
        if (calendarDays == 0) {
            confidence += 25;
        } else if (calendarDays == 1) {
            confidence += 20;
        } else if (calendarDays <= 3) {
            confidence += 10;
        }
        if (ownedAccountHint) {
            confidence += 20;
        }
        if (trading212Hint) {
            confidence += 20;
        }
        if (purchaseHint) {
            confidence -= 50;
        }
        return Math.max(0, Math.min(100, confidence));
    }

    public static String autoStatus(int confidence) {
        if (confidence >= 90) {
            return "LINKED";
        }
        if (confidence >= 70) {
            return "SUGGESTED";
        }
        return null;
    }
}
