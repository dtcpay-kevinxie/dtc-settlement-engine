package top.dtc.settlement.module.silvergate.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class WebHooksGetRegisterResp {

    @JsonProperty("WebHookId")
    public String webHookId;
    @JsonProperty("AccountNumber")
    public String accountNumber;
    @JsonProperty("Description")
    public String description;
    @JsonProperty("WebHookUrl")
    public String webHookUrl;
    @JsonProperty("Emails")
    public String emails;
    @JsonProperty("Sms")
    public String sms;
    @JsonProperty("ERROR")
    public List<Error> errorList;
}
