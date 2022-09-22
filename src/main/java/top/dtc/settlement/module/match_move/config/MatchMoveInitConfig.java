package top.dtc.settlement.module.match_move.config;

import kong.unirest.GenericType;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import top.dtc.addon.integration.match_move.domain.*;
import top.dtc.common.json.JSON;
import top.dtc.settlement.model.api.ApiRequest;
import top.dtc.settlement.module.match_move.properties.MatchMoveProperties;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Log4j2
@Configuration
public class MatchMoveInitConfig {

    @Autowired
    MatchMoveProperties matchMoveProperties;

    @PostConstruct
    public void init() {
        log.debug("Settlement engine init MatchMove WebhookRegister...");
        try {
            hookMatchMoveAccount();
        } catch (Exception e) {
            e.printStackTrace();
            log.error("MatchMove WebhookRegister error", e);
        }
    }

    private void hookMatchMoveAccount() {
        // Retrieve list of webhook categories
        HttpResponse<RetrieveWebhookCategoriesResp> retrieveWebhookCategoriesResp = Unirest.get(matchMoveProperties.integrationEngineEndpoint
                        + "/api/integration/match-move/webhook-categories")
                .asObject(new GenericType<RetrieveWebhookCategoriesResp>() {
                })
                .ifFailure(resp -> {
                    log.error("Call integration-engine {} failed, {}", "/match-move/webhooks-categories", resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error(e.getMessage()));
                });
        if (retrieveWebhookCategoriesResp.isSuccess()) {
            log.debug("Webhooks Categories: {}", JSON.stringify(retrieveWebhookCategoriesResp.getBody()));
        }
        // Retrieve list of webhooks
        HttpResponse<RetrieveWebhooksResp> retrieveWebhooksResp = Unirest.get(matchMoveProperties.integrationEngineEndpoint
                        + "/api/integration/match-move/webhooks")
                .asObject(new GenericType<RetrieveWebhooksResp>() {
                })
                .ifFailure(resp -> {
                    log.error("Call integration-engine {} failed, {}", "/match-move/webhooks", resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error(e.getMessage()));
                });
        if (retrieveWebhooksResp.isSuccess()) {
            log.debug("Webhook list: {}", JSON.stringify(retrieveWebhooksResp.getBody()));
            final RetrieveWebhooksResp webhooksResp = retrieveWebhooksResp.getBody();
            final List<RetrieveWebhooksResp.Event> eventList = webhooksResp.eventList;
            if (!eventList.isEmpty() && eventList.size() > 1) {
                for (RetrieveWebhooksResp.Event event : eventList) {
                    // Get registered webhooks
                    String webhookId = event.id;
                    final GetWebhookDetailResp webhookDetails = getWebhookDetails(webhookId);
                    if (webhookDetails != null && !webhookDetails.items.isEmpty() && webhookDetails.items.size() > 1) {
                        final List<GetWebhookDetailResp.Item> items = webhookDetails.items;
                        Map<String, GetWebhookDetailResp.Item> webhooksMap = items.stream()
                                .collect(Collectors.toMap(GetWebhookDetailResp.Item::getId, itemsResp -> itemsResp));
                        if (webhooksMap.containsKey(webhookId)
                                && matchMoveProperties.webhookUrl.equalsIgnoreCase(webhooksMap.get(webhookId).url)) {
                            log.info("Webhook registered");
                            return;
                        }
                    }
                    // Register webhook
                    CreateWebhooksReq createWebhooksReq = new CreateWebhooksReq();
                    createWebhooksReq.url = matchMoveProperties.webhookUrl;
                    createWebhooksReq.eventHash = webhookId;
                    registerWebhook(createWebhooksReq);
                }
            }
        }
    }


    private GetWebhookDetailResp getWebhookDetails(String webhookId) {
        final HttpResponse<GetWebhookDetailResp> webhookDetailResponse = Unirest.get(matchMoveProperties.integrationEngineEndpoint + "/api/integration/match-move/webhook/{}")
                .routeParam("webhookId", webhookId)
                .asObject(new GenericType<GetWebhookDetailResp>() {
                }).ifFailure(resp -> {
                    log.error("Call integration-engine Webhook detail API failed, {}", resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error(e.getMessage()));});
        if (webhookDetailResponse.isSuccess()) {
            log.debug("Webhook detail Resp: {}", JSON.stringify(webhookDetailResponse.getBody(), true));
            return webhookDetailResponse.getBody();
        }
        return null;
    }

    public void registerWebhook(CreateWebhooksReq createWebhooksReq) {
        final HttpResponse<CreateWebhooksResp> createWebhooksRespHttpResponse = Unirest.post(matchMoveProperties.integrationEngineEndpoint + "/api/integration/match-move/webhooks")
                .body(new ApiRequest<>(createWebhooksReq))
                .asObject(new GenericType<CreateWebhooksResp>() {
                })
                .ifFailure(resp -> {
                    log.error("Call integration-engine Create webhook API failed, {}", resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error(e.getMessage()));
                });
        if (createWebhooksRespHttpResponse.isSuccess()) {
            log.debug("Create Webhook Resp: {}", JSON.stringify(createWebhooksRespHttpResponse.getBody()));
        }
    }

}
