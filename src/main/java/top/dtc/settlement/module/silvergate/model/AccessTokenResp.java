package top.dtc.settlement.module.silvergate.model;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

/**
 * User: kevin.xie<br/>
 * Date: 25/02/2021<br/>
 * Time: 11:32<br/>
 */
@Data
public class AccessTokenResp {

    @JSONField(name = "Authorization")
    public String authorization;
    @JSONField(name = "api-supported-versions")
    public String requestContext;
    @JSONField(name = "Access-Control-Expose-Headers")
    public String accessControlExposeHeaders;
    @JSONField(name = "Set-Cookie")
    public String setCookie;
    @JSONField(name = "Site")
    public String site;
    @JSONField(name = "X-Powered-By")
    public String xPoweredBy;
    @JSONField(name = "Date")
    public String date;
    @JSONField(name = "Content-Length")
    public String contentLength;

}
