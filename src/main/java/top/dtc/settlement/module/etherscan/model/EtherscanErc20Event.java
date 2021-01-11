package top.dtc.settlement.module.etherscan.model;

import lombok.Data;

@Data
public class EtherscanErc20Event {

    public String blockNumber;
    public String timeStamp;
    public String hash;
    public String nonce;
    public String blockHash;
    public String from;
    public String contractAddress;
    public String to;
    public String value;
    public String tokenName;
    public String tokenSymbol;
    public String tokenDecimal;
    public String transactionIndex;
    public String gas;
    public String gasPrice;
    public String gasUsed;
    public String cumulativeGasUsed;
    public String input;
    public String confirmations;

}
