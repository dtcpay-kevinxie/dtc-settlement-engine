package top.dtc.settlement.report_processor.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import top.dtc.addon.data_processor.Record;
import top.dtc.addon.data_processor.RecordField;
import top.dtc.data.core.model.MonitoringMatrix;

@EqualsAndHashCode(callSuper = true)
@Data
@Record(mappings = {
        @RecordField(order = 0, title = "Client ID", path = "clientId")
})
public class AccountIssuedReport extends MonitoringMatrix {

//    @RecordField(order = 1, title = "Client Name")
//    public String clientName;

    @RecordField(order = 1, title = "Domestic Enabled")
    public String isDomesticEnabled;

    @RecordField(order = 2, title = "Cross-border Enabled")
    public String isCrossBorderEnabled;

    @RecordField(order = 3, title = "Merchant Acquisition Enabled")
    public String isMerchantAcquisitionEnabled;

    @RecordField(order = 4, title = "E-money Enabled")
    public String isEmoneyEnabled;

    @RecordField(order = 5, title = "DPT Enabled")
    public String isDptEnabled;

    @RecordField(order = 6, title = "Money Changing")
    public String isMoneyChangingEnabled;

}
