package top.dtc.settlement.module.silvergate.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * User: kevin.xie<br/>
 * Date: 04/03/2021<br/>
 * Time: 13:23<br/>
 */
@Data
public class Error {
    @JsonProperty("MESSAGEID")
    public String messageId;
    @JsonProperty("MESSAGETYPE")
    public String messageType;
    @JsonProperty("SEGMENTID")
    public String segmentId;
    @JsonProperty("SEGMENT_OCCUR")
    public String segment_occur;
    @JsonProperty("FIELDNAME")
    public String fieldName;
    @JsonProperty("ERRORMSG")
    public String errorMsg;
}
