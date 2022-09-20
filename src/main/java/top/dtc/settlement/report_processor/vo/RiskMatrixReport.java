package top.dtc.settlement.report_processor.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import top.dtc.addon.data_processor.DataRecord;
import top.dtc.addon.data_processor.RecordField;
import top.dtc.data.risk.model.RiskMatrix;

import static top.dtc.settlement.constant.DateConstant.FORMAT.DATE;

@Data
@EqualsAndHashCode(callSuper = true)
@DataRecord(mappings = {
        @RecordField(order = 0, title = "ID", path = "id"),
        @RecordField(order = 1, title = "Client ID", path = "clientId"),
        @RecordField(order = 2, title = "Risk Level", path = "riskLevel"),
        @RecordField(order = 3, title = "Verification Type", path = "verificationType"),
        @RecordField(order = 4, title = "Last Reviewed Date", path = "lastReviewedDate", format = DATE),
        @RecordField(order = 5, title = "Last Active Date", path = "lastActiveDate", format = DATE)
})
public class RiskMatrixReport extends RiskMatrix {

}
