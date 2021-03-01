package top.dtc.settlement.module.silvergate.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * User: kevin.xie<br/>
 * Date: 01/03/2021<br/>
 * Time: 15:39<br/>
 */
@Data
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
}
