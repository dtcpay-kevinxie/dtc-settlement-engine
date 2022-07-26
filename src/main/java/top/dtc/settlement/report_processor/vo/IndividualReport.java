package top.dtc.settlement.report_processor.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import top.dtc.data.core.model.Individual;
import top.dtc.settlement.handler.Record;
import top.dtc.settlement.handler.RecordField;

@EqualsAndHashCode(callSuper = true)
@Data
@Record(
        mappings = {
                @RecordField(order = 0, title = "ID", path = "id"),
                @RecordField(order = 1, title = "Status", path = "status"),
                @RecordField(order = 2, title = "Nick Name", path = "nickName"),
                @RecordField(order = 3, title = "Country", path = "country"),
                @RecordField(order = 4, title = "Created Date", path = "createdDate"),
        }
)
public class IndividualReport extends Individual {
}
