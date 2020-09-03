package top.dtc.settlement.exception;

public class ReconcileException extends RuntimeException {

    private final String responseCode;

    public ReconcileException(Throwable cause) {
        super(cause);
        this.responseCode = null;
    }

    public ReconcileException(final String message) {
        super(message);
        this.responseCode = null;
    }

    public ReconcileException(final String responseCode, final String message) {
        super(message);
        this.responseCode = responseCode;
    }

    public ReconcileException(final String responseCode, final String message, final Throwable throwable) {
        super(message, throwable);
        this.responseCode = responseCode;
    }

    public String getResponseCode() {
        return responseCode;
    }
}
