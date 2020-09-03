package top.dtc.settlement.exception;

public class ReceivableException extends RuntimeException {

    private final String responseCode;

    public ReceivableException(Throwable cause) {
        super(cause);
        this.responseCode = null;
    }

    public ReceivableException(final String message) {
        super(message);
        this.responseCode = null;
    }

    public ReceivableException(final String responseCode, final String message) {
        super(message);
        this.responseCode = responseCode;
    }

    public ReceivableException(final String responseCode, final String message, final Throwable throwable) {
        super(message, throwable);
        this.responseCode = responseCode;
    }

    public String getResponseCode() {
        return responseCode;
    }
}
