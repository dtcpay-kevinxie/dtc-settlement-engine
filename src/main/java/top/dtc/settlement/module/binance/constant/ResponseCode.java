package top.dtc.settlement.module.binance.constant;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.Maps;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;

@Getter
@AllArgsConstructor
public enum ResponseCode {

    USER_ASSET_NOT_ENOUGH    ("345110", "user asset not enough"),
    BINANCE_ASSET_NOT_ENOUGH ("345113", "binance asset not enough"),
    NOT_SUPPORT              ("345006", "not support"),
    SYSTEM_ERROR             ("345003", "system error"),
    ;

    @JsonValue
    @EnumValue
    public final String code;
    public final String message;

    public static final Map<String, ResponseCode> CODE_MAP = Maps.uniqueIndex(
            Arrays.asList(values()),
            ResponseCode::getCode
    );

    public static ResponseCode getMessage(String code) {
        return CODE_MAP.get(code);
    }

}
