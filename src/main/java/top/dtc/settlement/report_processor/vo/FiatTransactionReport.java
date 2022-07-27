package top.dtc.settlement.report_processor.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import top.dtc.data.core.model.FiatTransaction;
import top.dtc.settlement.handler.Record;
import top.dtc.settlement.handler.RecordField;

import java.math.BigDecimal;

import static top.dtc.settlement.handler.RecordFieldType.AMOUNT;
import static top.dtc.settlement.handler.RecordFieldType.ENUM_NAME;

@EqualsAndHashCode(callSuper = true)
@Data
@Record(
    mappings = {
            @RecordField(order = 0, title = "ID", path = "id", hidden = true),
            @RecordField(order = 1, title = "Client ID", path = "clientId"),
            @RecordField(order = 2, title = "Type", path = "type", type = ENUM_NAME),
            @RecordField(order = 3, title = "Status", path = "state", type = ENUM_NAME),
            @RecordField(order = 4, title = "Amount", path = "amount", type = AMOUNT, currencyPath = "currency"),
            @RecordField(order = 5, title = "Currency", path = "currency", type = ENUM_NAME),
            @RecordField(order = 6, title = "Date", path = "createdTime"),
})
public class FiatTransactionReport extends FiatTransaction {

    @RecordField(order = 7, title = "Rate to SGD")
    public BigDecimal rateToSGD;

    @RecordField(order = 8, title = "Country")
    public String recipientCountry;

}
