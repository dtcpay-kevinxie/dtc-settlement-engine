package top.dtc.settlement.report_processor.vo;

import top.dtc.addon.data_processor.DataRecord;
import top.dtc.addon.data_processor.RecordField;
import top.dtc.data.core.model.PaymentTransaction;

import static top.dtc.addon.data_processor.RecordFieldType.AMOUNT;
import static top.dtc.addon.data_processor.RecordFieldType.ENUM_NAME;
import static top.dtc.settlement.constant.DateConstant.FORMAT.DATETIME;

@DataRecord(mappings = {
        @RecordField(order = 0, title = "ID", path = "id"),
        @RecordField(order = 1, title = "Type", path = "type"),
        @RecordField(order = 2, title = "State", path = "state"),
        @RecordField(order = 3, title = "Merchant Name", path = "merchantName"),
        @RecordField(order = 4, title = "Terminal ID", path = "terminalId"),
        @RecordField(order = 5, title = "Brand", path = "brand"),
        @RecordField(order = 6, title = "Primary Account Number", path = "truncatedPan"),
        @RecordField(order = 7, title = "Currency", path = "requestCurrency", type = ENUM_NAME),
        @RecordField(order = 8, title = "Total Amount", path = "totalAmount", type = AMOUNT, currencyPath = "requestCurrency"),
        @RecordField(order = 9, title = "MDR", path = "mdr", type = AMOUNT, currencyPath = "requestCurrency"),
        @RecordField(order = 10, title = "Per Txn Fee", path = "processingFee", type = AMOUNT, currencyPath = "requestCurrency"),
        @RecordField(order = 11, title = "Settlement Currency", path = "settlementCurrency", type = ENUM_NAME),
        @RecordField(order = 12, title = "Settlement Amount", path = "settlementAmount", type = AMOUNT, currencyPath = "settlementCurrency"),
        @RecordField(order = 13, title = "Receipt Number", path = "receiptNumber"),
        @RecordField(order = 14, title = "Reference No", path = "referenceNo"),
        @RecordField(order = 15, title = "Transaction Time", path = "dtcTimestamp", format = DATETIME),
        @RecordField(order = 16, title = "Original ID", path = "originalId"),
})
public class SettlementTransactionReport extends PaymentTransaction {
}
