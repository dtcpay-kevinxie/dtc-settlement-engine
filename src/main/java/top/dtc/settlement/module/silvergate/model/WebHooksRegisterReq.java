package top.dtc.settlement.module.silvergate.model;

import lombok.Data;


@Data
public class WebHooksRegisterReq {

    public String accountNumber;
    public String description;
    public String webHookUrl;
    public String emails;
    public String sms;

}
