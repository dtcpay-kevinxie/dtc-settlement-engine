package top.dtc.settlement.module.binance.service;

import kong.unirest.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.addon.integration.binance.domain.QueryUserUnSettleReq;
import top.dtc.addon.integration.binance.domain.QueryUserUnSettleResp;
import top.dtc.addon.integration.binance.domain.SettleCreditOrdersResp;
import top.dtc.addon.integration.binance.domain.SettleCreditOrdersReq;
import top.dtc.addon.integration.notification.NotificationEngineClient;
import top.dtc.common.exception.DtcRuntimeException;
import top.dtc.common.json.JSON;
import top.dtc.common.model.api.ApiRequest;
import top.dtc.common.model.api.ApiResponse;
import top.dtc.common.web.Endpoints;
import top.dtc.settlement.core.properties.NotificationProperties;
import top.dtc.settlement.module.binance.constant.ResponseCode;

import java.util.Map;

import static top.dtc.settlement.constant.NotificationConstant.NAMES.QUERY_USER_UNSETTLE;

@Log4j2
@Service
public class BinanceSettleService {

    @Autowired
    Endpoints endpoints;

    @Autowired
    NotificationProperties notificationProperties;

    @Autowired
    NotificationEngineClient notificationEngineClient;

    public void queryUserUnsettle() {
        QueryUserUnSettleReq queryUserUnSettleReq = new QueryUserUnSettleReq();
        queryUserUnSettleReq.endTime = System.currentTimeMillis();
        RequestBodyEntity requestBodyEntity = Unirest.post(endpoints.INTEGRATION_ENGINE + "/api/integration/binance/query-user-unsettle")
                .header(HeaderNames.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
                .body(new ApiRequest<>(queryUserUnSettleReq));
        log.debug("Request url: {}", requestBodyEntity.getUrl());
        ApiResponse<QueryUserUnSettleResp> queryUserUnSettleResp = requestBodyEntity.asObject(
                new GenericType<ApiResponse<QueryUserUnSettleResp>>() {
                }).getBody();
        log.debug("Request body: {}", JSON.stringify(queryUserUnSettleReq));
        if (queryUserUnSettleResp == null ||
                !queryUserUnSettleResp.header.success
                || queryUserUnSettleResp.result.DetailList == null
                || queryUserUnSettleResp.result.DetailList.size() < 1) {
            log.error("User Unsettle Query API Failed {}", JSON.stringify(queryUserUnSettleResp, true));
        } else {
            notificationEngineClient
                    .by(QUERY_USER_UNSETTLE)
                    .to(notificationProperties.opsRecipient)
                    .dataMap(Map.of(
                            "details", queryUserUnSettleResp.result.DetailList + "\n"
                    ))
                    .send();
        }
    }

    public void settleCreditOrders() {
        SettleCreditOrdersReq settleCreditOrdersReq = new SettleCreditOrdersReq();
        settleCreditOrdersReq.endTime = System.currentTimeMillis();
        RequestBodyEntity requestBodyEntity = Unirest.post(endpoints.INTEGRATION_ENGINE + "/api/integration/binance/settle-credit-orders")
                .header(HeaderNames.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
                .body(new ApiRequest<>(settleCreditOrdersReq));
        log.debug("Request url: {}", requestBodyEntity.getUrl());
        ApiResponse<SettleCreditOrdersResp> settleCreditOrderResp = requestBodyEntity.asObject(new GenericType<ApiResponse<SettleCreditOrdersResp>>() {}).getBody();
        log.debug("Request body: {}", JSON.stringify(settleCreditOrdersReq));
        if (settleCreditOrderResp == null || settleCreditOrderResp.header == null) {
            throw new DtcRuntimeException("Error when connecting integration-engine");
        } else if (!settleCreditOrderResp.header.success) {
            throw new DtcRuntimeException(ResponseCode.getMessage(settleCreditOrderResp.result.code).message);
        }
        log.debug("Request result: {}", settleCreditOrderResp.result);
    }

}
