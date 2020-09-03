package top.dtc.settlement.exception;

public class SettlementException extends RuntimeException {

    private final String responseCode;

    public SettlementException(Throwable cause) {
        super(cause);
        this.responseCode = null;
    }

    public SettlementException(final String message) {
        super(message);
        this.responseCode = null;
    }

    public SettlementException(final String responseCode, final String message) {
        super(message);
        this.responseCode = responseCode;
    }

    public SettlementException(final String responseCode, final String message, final Throwable throwable) {
        super(message, throwable);
        this.responseCode = responseCode;
    }

    public String getResponseCode() {
        return responseCode;
    }
}
