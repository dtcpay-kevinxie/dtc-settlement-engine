package top.dtc.settlement.report_processor.vo;

import top.dtc.addon.data_processor.DataRecord;
import top.dtc.addon.data_processor.RecordField;
import top.dtc.data.core.model.Otc;

import java.math.BigDecimal;

import static top.dtc.addon.data_processor.RecordFieldType.AMOUNT;
import static top.dtc.addon.data_processor.RecordFieldType.ENUM_NAME;
import static top.dtc.settlement.constant.DateConstant.FORMAT.DATETIME;

@DataRecord(mappings = {
        @RecordField(order = 0, title = "ID", path = "id"),
        @RecordField(order = 1, title = "Client ID", path = "clientId"),
        @RecordField(order = 1, title = "Type", path = "type", type = ENUM_NAME),
        @RecordField(order = 3, title = "Crypto Currency", path = "cryptoCurrency", type = ENUM_NAME),
        @RecordField(order = 4, title = "Crypto Amount", path = "cryptoAmount"),
        @RecordField(order = 5, title = "Fiat Currency", path = "fiatCurrency", type = ENUM_NAME),
        @RecordField(order = 6, title = "Fiat Amount", path = "fiatAmount", type = AMOUNT, currencyPath = "fiatCurrency"),
        @RecordField(order = 7, title = "Rate", path = "rate"),
        @RecordField(order = 9, title = "Completed Time", path = "completedTime", format = DATETIME),
})
public class OtcReport extends Otc {

    @RecordField(order = 8, title = "Rate to SGD")
    public BigDecimal rateToSGD;

}
