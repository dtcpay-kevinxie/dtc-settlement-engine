package top.dtc.settlement.module.aletapay.model;

import lombok.Data;
import top.dtc.settlement.handler.RecordField;

import java.util.Date;
import java.util.List;

@Data
public class AletaSettlementReport {

    public List<Record> records;

    @Data
    public static class Record {
        @RecordField(order = 0)
        public String num;
        @RecordField(order = 1)
        public String txnType;
        @RecordField(order = 2)
        public String orderId;
        @RecordField(order = 3)
        public String pan;
        @RecordField(order = 4, format = "yyyyMMddHHmmss")
        public Date txnTime;
        @RecordField(order = 5)
        public String traceNumber;
        @RecordField(order = 6)
        public String terminalId;
        @RecordField(order = 7)
        public String txnAmount;
        @RecordField(order = 8)
        public String txnCurrency;
        @RecordField(order = 9)
        public String settlementAmount;
        @RecordField(order = 10)
        public String settlementCurrency;
    }
}
