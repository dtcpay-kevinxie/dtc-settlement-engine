package top.dtc.settlement.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OtcAgreeResult {

    public boolean success;
    public Long payableId;
    public Long receivableId;

    public OtcAgreeResult(boolean success) {
        this.success = success;
    }

}
