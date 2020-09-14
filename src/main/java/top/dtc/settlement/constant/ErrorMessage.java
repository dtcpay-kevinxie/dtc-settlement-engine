package top.dtc.settlement.constant;

public class ErrorMessage {

    public static final class RECONCILE {

        public static String INVALID_TRANSACTION_ID(Long transactionId) {
            return String.format("Unable to reconcile transaction %s", transactionId);
        }

        public static String INVALID_TRANSACTION_ID(Long transactionId, String state) {
            return String.format("Unable to reconcile transaction %s due to TransactionState [%s]", transactionId, state);
        }

    }

    public static final class SETTLEMENT {
        public static String INVALID(Long settlementId) {
            return String.format("Invalid Settlement [%s]", settlementId);
        }
        public static String STATUS_FAILED(Long merchantId, String merchantStatus) {
            return String.format("Merchant %s settlement disabled due to MerchantStatus [%s]", merchantId, merchantStatus);
        }
        public static final String APPROVAL_FAILED = "Unable to approve Settlement ";
        public static final String REJECT_FAILED = "Unable to reject Settlement ";
        public static final String RETRIEVE_FAILED = "Unable to retrieve Settlement Submission.";
        public static String INCLUDE_FAILED(Long transactionId) {
            return String.format("Unable to include Transaction %s", transactionId);
        }
        public static String INCLUDE_FAILED(Long transactionId, String type) {
            return String.format("Unable to include Transaction %s type is [%s]", transactionId, type);
        }
        public static final String EXCLUDE_FAILED = "Unable to exclude Transaction ";
        public static String EXCLUDE_FAILED(Long transactionId) {
            return String.format("Unable to exclude Transaction %s", transactionId);
        }
        public static String DUPLICATED_PAY(Long transactionId, Long payableId) {
            return String.format("Transaction %s has been paid out in Payable %s", transactionId, payableId);
        }
    }

    public static final class ADJUSTMENT {
        public static final String ADDING_FAILED = "Failed to add Adjustment ";
        public static String REMOVING_FAILED(Long adjustmentId, Long settlementId) {
            return String.format("Unable to approve Adjustment %s from %s", adjustmentId, settlementId);
        }
    }

    public static final class RESERVE {
        public static final String INVALID_CONFIG = "Invalid Reserve Config";
        public static final String INVALID_RESERVE = "Invalid Reserve";
        public static String INVALID_RESERVE_ID(Long reserveId) {
            return String.format("Invalid Reserve Config [%s]", reserveId);
        }
        public static String RESET_FAILED(Long reserveId, String reserveStatus) {
            return String.format("Unable to reset Reserve %s due to ReserveStatus is [%s]", reserveId, reserveStatus);
        }
    }

    public static final class RECEIVABLE {

        public static String RECEIVABLE_TRANSACTION_ID(Long receivableId) {
            return String.format("There are Transactions under Receivable %s", receivableId);
        }

        public static String INVALID_RECEIVABLE_ID(Long receivableId) {
            return String.format("Couldn't find Receivable by receivableId [%s]", receivableId);
        }

        public static String INVALID_RECEIVABLE_REF(String referenceNo) {
            return String.format("Couldn't find Receivable by referenceNo [%s]", referenceNo);
        }

    }

}
