package top.dtc.settlement.model.api;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class ApiHeader {

    public boolean success;

    public String errCode;

    public String errMsg;

    public ApiHeader(boolean success) {
        this.success = success;
    }

    public ApiHeader(String errCode, String errMsg) {
        this.success = false;
        this.errCode = errCode;
        this.errMsg = errMsg;
    }

}
