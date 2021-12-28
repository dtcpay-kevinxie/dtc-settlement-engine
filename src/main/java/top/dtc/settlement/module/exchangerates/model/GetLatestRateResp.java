package top.dtc.settlement.module.exchangerates.model;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;

@Data
public class GetLatestRateResp {

    public Boolean success;

    public Long timestamp;

    public String base; // base currency

    public String date;

    public JSONObject rates; // output currency rate


}
