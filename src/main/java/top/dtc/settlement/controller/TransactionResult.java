package top.dtc.settlement.controller;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class TransactionResult {

    public String hash;

    public int networkId;

    public List<ContractResult> contracts = new ArrayList<>();

    public BlockResult block = new BlockResult();

    public Boolean success;

    @Data
    public static class ContractResult {
        public String type;
        public String name;
        public String address;
        public String from;
        public String to;
        public BigDecimal amount;
    }

    @Data
    public static class BlockResult {
        public long number;
        public long last;
        public LocalDateTime datetime;
    }

}
