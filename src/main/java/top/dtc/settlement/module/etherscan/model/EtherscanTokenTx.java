package top.dtc.settlement.module.etherscan.model;

import lombok.Data;

import java.util.List;

@Data
public class EtherscanTokenTx {

    public String message;
    public String status;
    public List<EtherscanErc20Event> result;

}
