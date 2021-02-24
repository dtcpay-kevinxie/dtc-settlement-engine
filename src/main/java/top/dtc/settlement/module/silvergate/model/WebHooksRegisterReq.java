package top.dtc.settlement.module.silvergate.model;

import lombok.Data;

/**
 * User: kevin.xie<br/>
 * Date: 22/02/2021<br/>
 * Time: 17:28<br/>
 */
@Data
public class WebHooksRegisterReq {

    public String accountNumber;

    public String description;

    public String webHookUrl;
    public String emails;
    public String sms;

}
