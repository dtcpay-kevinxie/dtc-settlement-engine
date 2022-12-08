package top.dtc.settlement.module.silvergate.v3.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class WebHooksGetResp {

    public String count;
    public List<Record> records;

    static final class Record {
        @JsonProperty("web_hook_id")
        public String webHookId;
        @JsonProperty("account_number")
        public String accountNumber;
        @JsonProperty("description")
        public String description;
        @JsonProperty("web_hook_url")
        public String webHookUrl;
        @JsonProperty("email")
        public String emails;
    }

}
