package top.dtc.settlement.report_processor.vo;

import top.dtc.addon.data_processor.DataRecord;
import top.dtc.addon.data_processor.RecordField;
import top.dtc.data.core.model.CryptoTransaction;

import java.math.BigDecimal;

import static top.dtc.addon.data_processor.RecordFieldType.ENUM_NAME;
import static top.dtc.settlement.constant.DateConstant.FORMAT.DATETIME;

@DataRecord(
     mappings = {
             @RecordField(order = 0, title = "ID", path = "id"),
             @RecordField(order = 1, title = "Client ID", path = "clientId"),
             @RecordField(order = 2, title = "Type", path = "type", type = ENUM_NAME),
             @RecordField(order = 3, title = "Network", path = "mainNet", type = ENUM_NAME),
             @RecordField(order = 4, title = "Crypto", path = "currency", type = ENUM_NAME),
             @RecordField(order = 5, title = "Amount", path = "amount"),
             @RecordField(order = 6, title = "Request Timestamp", path = "requestTimestamp", format = DATETIME),
})
public class CryptoTransactionReport extends CryptoTransaction {

    @RecordField(order = 7, title = "Rate to SGD")
    public BigDecimal rateToSGD;

}
