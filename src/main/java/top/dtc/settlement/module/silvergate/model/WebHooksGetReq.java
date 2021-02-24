package top.dtc.settlement.module.silvergate.model;

import lombok.Data;

/**
 * User: kevin.xie<br/>
 * Date: 23/02/2021<br/>
 * Time: 20:15<br/>
 */
@Data
public class WebHooksGetReq {

    public String accountNumber;
    public String webHookId;
}
