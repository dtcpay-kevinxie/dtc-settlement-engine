package top.dtc.settlement.module.ftx.model;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

@Data
public class OtcPairsResponse {

    @JSONField(defaultValue = "id")
    public String id;

    @JSONField(defaultValue = "baseCurrency")
    public String baseCurrency;

    @JSONField(defaultValue = "quoteCurrency")
    public String quoteCurrency;

}
