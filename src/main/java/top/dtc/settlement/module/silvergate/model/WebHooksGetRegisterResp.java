package top.dtc.settlement.module.silvergate.model;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.util.List;

@Data
public class WebHooksGetRegisterResp {

    @JSONField(name = "WebHookId")
    public String webHookId;
    @JSONField(name = "AccountNumber")
    public String accountNumber;
    @JSONField(name = "Description")
    public String description;
    @JSONField(name = "WebHookUrl")
    public String webHookUrl;
    @JSONField(name = "Emails")
    public String emails;
    @JSONField(name = "Sms")
    public String sms;
    @JSONField(name = "ERROR")
    public List<Error> errorList;
}
