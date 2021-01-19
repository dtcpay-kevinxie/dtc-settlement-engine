package top.dtc.settlement.module.etherscan.model;

import lombok.Data;

@Data
public class EtherscanTxnByHash {

    public String jsonrpc;
    public long id;
    public EtherscanTxnByHashResult result;

}
