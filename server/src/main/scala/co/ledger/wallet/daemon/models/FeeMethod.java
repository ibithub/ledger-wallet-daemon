package co.ledger.wallet.daemon.models;

public enum FeeMethod {

    SLOW, NORMAL, FAST;

    public static FeeMethod from(String method) {
        try {
            return FeeMethod.valueOf(method.toUpperCase());
        } catch ( IllegalArgumentException e) {
            throw new IllegalArgumentException("Fee method undefined '" + method + "'");
        }
    }

    public static boolean isValid(String level) {
        try {
            FeeMethod.from(level);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
