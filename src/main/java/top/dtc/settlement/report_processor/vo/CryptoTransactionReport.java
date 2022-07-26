package top.dtc.settlement.report_processor.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import top.dtc.data.core.model.CryptoTransaction;
import top.dtc.settlement.handler.Record;
import top.dtc.settlement.handler.RecordField;

import static top.dtc.settlement.constant.DateConstant.FORMAT.DATETIME;
import static top.dtc.settlement.handler.RecordFieldType.ENUM_NAME;

@EqualsAndHashCode(callSuper = true)
@Data
@Record(
     mappings = {
             @RecordField(order = 0, title = "ID", path = "id"),
             @RecordField(order = 1, title = "Client ID", path = "clientId"),
             @RecordField(order = 2, title = "Type", path = "type"),
             @RecordField(order = 3, title = "Network", path = "mainNet"),
             @RecordField(order = 4, title = "Crypto", path = "currency", type = ENUM_NAME),
             @RecordField(order = 5, title = "Amount", path = "amount"),
             @RecordField(order = 6, title = "Request Timestamp", path = "requestTimestamp", format = DATETIME),
})
public class CryptoTransactionReport extends CryptoTransaction {

    @RecordField(order = 7, title = "Rate to SGD")
    public String rateToSGD;

}
