package top.dtc.settlement.module.ftx.model;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

@Data
public class OtcPairsResponse {

    @JSONField(name = "id")
    public String id;

    @JSONField(name = "baseCurrency")
    public String baseCurrency;

    @JSONField(name = "quoteCurrency")
    public String quoteCurrency;

}
