package top.dtc.settlement.module.silvergate.v3.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class WebHooksRegisterReq {

    @JsonProperty("account_number")
    public String accountNumber;
    @JsonProperty("description")
    public String description;
    @JsonProperty("web_hook_url")
    public String webHookUrl;
    @JsonProperty("emails")
    public String emails;

}
