package top.dtc.settlement.module.etherscan.model;

import lombok.Data;

@Data
public class EtherscanTxnByHashResult {

    public String blockHash;
    public String blockNumber;
    public String from;
    public String gas;
    public String gasPrice;
    public String hash;
    public String input;
    public String nonce;
    public String to;
    public String transactionIndex;
    public String value;
    public String v;
    public String r;
    public String s;

}
