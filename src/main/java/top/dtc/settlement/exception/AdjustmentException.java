package top.dtc.settlement.exception;

public class AdjustmentException extends RuntimeException {

    private final String responseCode;

    public AdjustmentException(Throwable cause) {
        super(cause);
        this.responseCode = null;
    }

    public AdjustmentException(final String message) {
        super(message);
        this.responseCode = null;
    }

    public AdjustmentException(final String responseCode, final String message) {
        super(message);
        this.responseCode = responseCode;
    }

    public AdjustmentException(final String responseCode, final String message, final Throwable throwable) {
        super(message, throwable);
        this.responseCode = responseCode;
    }

    public String getResponseCode() {
        return responseCode;
    }
}
