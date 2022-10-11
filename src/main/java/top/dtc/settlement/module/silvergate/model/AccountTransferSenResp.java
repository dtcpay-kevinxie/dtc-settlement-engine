package top.dtc.settlement.module.silvergate.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class AccountTransferSenResp {

    @JsonProperty("SEQUENCE")
    public Integer sequence;

    @JsonProperty("MESSAGE")
    public List<Message> messages;

    @JsonProperty("ERROR")
    public List<Error> errors;

    public static class Message {
        @JsonProperty("MESSAGEID")
        public String messageId;
        @JsonProperty("MESSAGETYPE")
        public String messageType;
        @JsonProperty("MESSAGETEXT")
        public String messageText;
    }

    public static class Error {
        @JsonProperty("MESSAGEID")
        public String messageId;
        @JsonProperty("SEGMENTID")
        public String segmentId;
        @JsonProperty("FIELDNAME")
        public String fieldName;
        @JsonProperty("ERRORMSG")
        public String errorMsg;
    }
}
