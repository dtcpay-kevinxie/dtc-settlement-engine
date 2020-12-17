package top.dtc.settlement.module.etherscan.model;

import lombok.Data;

@Data
public class EtherscanTxnVo {

    public String jsonrpc;
    public long id;
    public EtherscanTxnResultVo result;

}
