package top.dtc.settlement.report_processor.vo;

import top.dtc.addon.data_processor.DataRecord;
import top.dtc.addon.data_processor.RecordField;
import top.dtc.data.core.model.NonIndividual;

import static top.dtc.settlement.constant.DateConstant.FORMAT.DATETIME;

@DataRecord(mappings = {
        @RecordField(order = 0, title = "ID", path = "id"),
        @RecordField(order = 1, title = "Type", path = "type"),
        @RecordField(order = 2, title = "Status", path = "status"),
        @RecordField(order = 3, title = "Operating Full Name", path = "fullName"),
        @RecordField(order = 4, title = "Short Name", path = "shortName"),
        @RecordField(order = 6, title = "Remark", path = "remark"),
        @RecordField(order = 7, title = "Country", path = "country"),
        @RecordField(order = 12, title = "Created Date", path = "createdDate", format = DATETIME),
})
public class NonIndividualReport extends NonIndividual {

}
