package top.dtc.settlement.module.silvergate.model;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PaymentPostResp {
    @JSONField(name = "payment_id")
    public String payment_id;
    @JSONField(name = "payment_status")
    public String status;
    @JSONField(name = "payment_timestamp")
    public LocalDateTime timestamp;

    @JSONField(name = "ERROR")
    public List<Error> errorList;
}
