package top.dtc.settlement.module.silvergate.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * User: kevin.xie<br/>
 * Date: 01/03/2021<br/>
 * Time: 15:25<br/>
 */
@Data
public class PaymentPutResp {
    public String payment_id;
    public String payment_status;
    public String payment_timestamp;

    @JsonProperty("ERROR")
    public List<Error> error;

    @Data
    static class Error {

    }
}
