package top.dtc.settlement.module.silvergate.model;

import lombok.Data;

/**
 * User: kevin.xie<br/>
 * Date: 01/03/2021<br/>
 * Time: 15:39<br/>
 */
@Data
public class WebHookGetResp {

    public String webHookId;
    public String accountNumber;
    public String description;
    public String webHookUrl;
    public String emails;
    public String sms;
}
