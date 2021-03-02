package top.dtc.settlement.module.silvergate.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * User: kevin.xie<br/>
 * Date: 23/02/2021<br/>
 * Time: 20:01<br/>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentGetReq implements Serializable {

    @JsonProperty("account_number")
    public String accountNumber;// required
    @JsonProperty("payment_id")
    public String paymentId;
    @JsonProperty("begin_date")
    public String beginDate;
    @JsonProperty("end_date")
    public String endDate;
    @JsonProperty("sort_order")
    public String sortOrder;
    @JsonProperty("page_size")
    public Integer pageSize;
    @JsonProperty("page_number")
    public Integer pageNumber;
}
