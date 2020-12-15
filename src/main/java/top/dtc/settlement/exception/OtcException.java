package top.dtc.settlement.exception;

public class OtcException extends RuntimeException {

    private final String responseCode;

    public OtcException(Throwable cause) {
        super(cause);
        this.responseCode = null;
    }

    public OtcException(final String message) {
        super(message);
        this.responseCode = null;
    }

    public OtcException(final String responseCode, final String message) {
        super(message);
        this.responseCode = responseCode;
    }

    public OtcException(final String responseCode, final String message, final Throwable throwable) {
        super(message, throwable);
        this.responseCode = responseCode;
    }

    public String getResponseCode() {
        return responseCode;
    }
}
