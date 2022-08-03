package top.dtc.settlement.report_processor.vo;

import top.dtc.data.core.model.PaymentTransaction;
import top.dtc.settlement.handler.Record;
import top.dtc.settlement.handler.RecordField;

import java.math.BigDecimal;

import static top.dtc.settlement.constant.DateConstant.FORMAT.DATETIME;
import static top.dtc.settlement.handler.RecordFieldType.AMOUNT;
import static top.dtc.settlement.handler.RecordFieldType.ENUM_NAME;


@Record(mappings = {
        @RecordField(order = 0, title = "ID", path = "id"),
        @RecordField(order = 1, title = "Client ID", path = "merchantId"),
        @RecordField(order = 2, title = "Client Name", path = "merchantName"),
        @RecordField(order = 3, title = "Brand", path = "brand", type = ENUM_NAME),
        @RecordField(order = 4, title = "Currency", path = "requestCurrency", type = ENUM_NAME),
        @RecordField(order = 5, title = "Total Amount", path = "totalAmount", type = AMOUNT, currencyPath = "requestCurrency"),
        @RecordField(order = 7, title = "Transaction Time", path = "dtcTimestamp", format = DATETIME),
        @RecordField(order = 8, title = "Country", path = "country"),
})
public class PaymentTransactionReport extends PaymentTransaction {

    @RecordField(order = 6, title = "Rate to SGD")
    public BigDecimal rateToSGD;

}
