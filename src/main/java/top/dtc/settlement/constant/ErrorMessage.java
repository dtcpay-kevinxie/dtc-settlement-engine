package top.dtc.settlement.constant;

import com.alibaba.fastjson.JSON;
import top.dtc.settlement.module.silvergate.model.PaymentPostResp;
import top.dtc.settlement.module.silvergate.model.PaymentPutResp;

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

        public static final String APPROVAL_FAILED = "Unable to approve Settlement ";
        public static final String REJECT_FAILED = "Unable to reject Settlement ";
        public static final String RETRIEVE_FAILED = "Unable to retrieve Settlement Submission.";
        public static final String EXCLUDE_FAILED = "Unable to exclude Transaction ";

        public static String INVALID(Long settlementId) {
            return String.format("Invalid Settlement [%s]", settlementId);
        }

        public static String STATUS_FAILED(Long merchantId, String merchantStatus) {
            return String.format("Merchant %s settlement disabled due to MerchantStatus [%s]", merchantId, merchantStatus);
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

        public static final String INVALID_RECEIVABLE_PARA = "Receivable referenceNo/amount/receivableId is invalid.";
        public static final String INVALID_RECEIVABLE = "Invalid Receivable";
        public static final String INVALID_RECEIVABLE_STATUS = "Invalid Receivable Status";
        public static final String CANCEL_RECEIVABLE_ERROR = "Can not cancel receivable.";

    }

    public static final class PAYABLE {
        public static final String INVALID_PAYABLE = "Invalid Payable";
        public static final String INVALID_PAYABLE_PARA = "Payable referenceNo/payableId is invalid.";
        public static final String CANCEL_PAYABLE_ERROR = "Can not cancel payable.";
        public static String OTC_NOT_RECEIVED(Long otcId) {
            return String.format("Fund of OTC Order %s not yet received.", otcId);
        }
        public static String INVALID_PAYABLE_ID(Long payableId) {
            return String.format("Couldn't find Payable by payableId [%s]", payableId);
        }
        public static final String PAYABLE_WROTE_OFF = "Payable is written-off already";
        public static String PAYMENT_INIT_FAILED(Long payableId, PaymentPostResp resp) {
            return String.format( "Couldn't initial payment via Silvergate for Payable [%s] due to [%s]",
                    payableId,
                    (resp == null) ? "Empty Reponse from Silvergate" : "Status:" + resp.status
            );
        }
        public static String PAYMENT_CANCEL_FAILED(Long payableId, PaymentPutResp resp) {
            return String.format("Couldn't cancel payment for Payable [%s] due to [%s]",
                    payableId,
                    (resp == null) ? "Empty Reponse from Silvergate" : "Error:" + JSON.toJSONString(resp.errorList)
            );
        }
        public static String SILVERGATE_TOKEN_RETRIEVAL_FAILED(String accountNumber) {
            return String.format("Couldn't retrieve Access Token by AccountNumber %s", accountNumber);
        }
        public static String SILVERGATE_ACCOUNT_NUMBER_NOT_REGISTERED(String accountNumber) {
            return String.format("Silvergate Account Number %s Not Registered in System", accountNumber);
        }
        public static String SILVERGATE_INVALID_ACCOUNT_TYPE(String accountType) {
            return String.format("Invalid Silvergate Account Type %s ", accountType);
        }
        public static String SILVERGATE_SUBSCRIPTION_KEY_NOT_REGISTERED(String accountNumber) {
            return String.format("Silvergate Account Number %s Not Registered in System", accountNumber);
        }
    }

    public static final class OTC {
        public static final String HIGH_RISK_OTC = "The OTC is in high risk, please stop transaction and check client risk level.";
        public static final String INVALID_OTC = "Invalid OTC";
    }

}
