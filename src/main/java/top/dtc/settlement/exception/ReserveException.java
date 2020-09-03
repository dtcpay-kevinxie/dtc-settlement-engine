package top.dtc.settlement.exception;

public class ReserveException extends RuntimeException {

    private final String responseCode;

    public ReserveException(Throwable cause) {
        super(cause);
        this.responseCode = null;
    }

    public ReserveException(final String message) {
        super(message);
        this.responseCode = null;
    }

    public ReserveException(final String responseCode, final String message) {
        super(message);
        this.responseCode = responseCode;
    }

    public ReserveException(final String responseCode, final String message, final Throwable throwable) {
        super(message, throwable);
        this.responseCode = responseCode;
    }

    public String getResponseCode() {
        return responseCode;
    }
}
