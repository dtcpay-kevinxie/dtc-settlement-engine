package top.dtc.settlement.model.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiHeader {

    public boolean success;

    public Integer errorCode;

    public String errorMessage;

    public ApiHeader(boolean success) {
        this.success = success;
    }

    public ApiHeader(String errorMessage) {
        this.success = false;
        this.errorMessage = errorMessage;
    }

}
