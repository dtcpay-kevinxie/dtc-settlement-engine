package top.dtc.settlement.module.silvergate.model;

import lombok.Data;

@Data
public class WebHooksGetReq {

    public String accountNumber;
    public String webHookId;
}
