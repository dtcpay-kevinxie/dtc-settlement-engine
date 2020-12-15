package top.dtc.settlement.model.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
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
