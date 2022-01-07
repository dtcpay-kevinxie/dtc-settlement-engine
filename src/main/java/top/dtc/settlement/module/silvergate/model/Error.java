package top.dtc.settlement.module.silvergate.model;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

@Data
public class Error {
    @JSONField(name = "MESSAGEID")
    public String messageId;
    @JSONField(name = "MESSAGETYPE")
    public String messageType;
    @JSONField(name = "SEGMENTID")
    public String segmentId;
    @JSONField(name = "SEGMENT_OCCUR")
    public String segment_occur;
    @JSONField(name = "FIELDNAME")
    public String fieldName;
    @JSONField(name = "ERRORMSG")
    public String errorMsg;
}
