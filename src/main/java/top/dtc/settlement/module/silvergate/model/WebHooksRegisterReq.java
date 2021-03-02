package top.dtc.settlement.module.silvergate.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;


/**
 * User: kevin.xie<br/>
 * Date: 22/02/2021<br/>
 * Time: 17:28<br/>
 */
@Data
public class WebHooksRegisterReq {

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
