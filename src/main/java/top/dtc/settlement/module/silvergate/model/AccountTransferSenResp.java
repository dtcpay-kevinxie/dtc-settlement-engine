package top.dtc.settlement.module.silvergate.model;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.util.List;

/**
 * User: kevin.xie<br/>
 * Date: 07/05/2021<br/>
 * Time: 17:15<br/>
 */
@Data
public class AccountTransferSenResp {

    @JSONField(name = "SEQUENCE")
    public Integer sequence;

    @JSONField(name = "MESSAGE")
    public List<Message> messages;

    @JSONField(name = "ERROR")
    public List<Error> errors;

    @Data
    public static class Message {
        @JSONField(name = "MESSAGEID")
        public String messageId;
        @JSONField(name = "MESSAGETYPE")
        public String messageType;
        @JSONField(name = "MESSAGETEXT")
        public String messageText;
    }

    @Data
    public static class Error {
        @JSONField(name = "MESSAGEID")
        public String messageId;
        @JSONField(name = "SEGMENTID")
        public String segmentId;
        @JSONField(name = "FIELDNAME")
        public String fieldName;
        @JSONField(name = "ERRORMSG")
        public String errorMsg;
    }
}
