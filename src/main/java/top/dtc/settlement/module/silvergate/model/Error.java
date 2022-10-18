package top.dtc.settlement.module.silvergate.model;

import com.fasterxml.jackson.annotation.JsonProperty;

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
