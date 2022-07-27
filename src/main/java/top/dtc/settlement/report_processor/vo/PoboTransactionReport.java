package top.dtc.settlement.report_processor.vo;

import top.dtc.data.core.model.PoboTransaction;
import top.dtc.settlement.handler.Record;
import top.dtc.settlement.handler.RecordField;

import java.math.BigDecimal;

import static top.dtc.settlement.constant.DateConstant.FORMAT.DATETIME;
import static top.dtc.settlement.handler.RecordFieldType.*;


@Record(mappings = {
        @RecordField(order = 0, title = "ID", path = "id"),
        @RecordField(order = 1, title = "Type", path = "type", type = ENUM_NAME),
        @RecordField(order = 2, title = "State", path = "state", type = ENUM_NAME),
        @RecordField(order = 3, title = "Client ID", path = "clientId"),
        @RecordField(order = 4, title = "Sender Currency", path = "senderCurrency", type = ENUM_NAME),
        @RecordField(order = 5, title = "Originator Amount", path = "originatorAmount", type = AMOUNT, currencyPath = "senderCurrency"),
        @RecordField(order = 6, title = "Exchange Rate", path = "rate", type = PERCENTAGE),
        @RecordField(order = 7, title = "Recipient Currency", path = "recipientCurrency", type = ENUM_NAME),
        @RecordField(order = 8, title = "Recipient Amount", path = "recipientAmount", type = AMOUNT, currencyPath = "recipientCurrency"),
        @RecordField(order = 9, title = "Transaction Fee", path = "transactionFee", type = AMOUNT, currencyPath = "senderCurrency"),
        @RecordField(order = 10, title = "Purpose", path = "remark"),
        @RecordField(order = 11, title = "Approved Time", path = "approvedTime", format = DATETIME),
})
public class PoboTransactionReport extends PoboTransaction {

    @RecordField(order = 12, title = "Country")
    public String recipientCountry;

    @RecordField(order = 13, title = "Rate to SGD")
    public BigDecimal rateToSGD;

}

