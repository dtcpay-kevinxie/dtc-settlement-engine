package top.dtc.settlement.module.silvergate.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AccessTokenResp {

    @JsonProperty("Authorization")
    public String authorization;
    @JsonProperty("api-supported-versions")
    public String requestContext;
    @JsonProperty("Access-Control-Expose-Headers")
    public String accessControlExposeHeaders;
    @JsonProperty("Set-Cookie")
    public String setCookie;
    @JsonProperty("Site")
    public String site;
    @JsonProperty("X-Powered-By")
    public String xPoweredBy;
    @JsonProperty("Date")
    public String date;
    @JsonProperty("Content-Length")
    public String contentLength;

}
