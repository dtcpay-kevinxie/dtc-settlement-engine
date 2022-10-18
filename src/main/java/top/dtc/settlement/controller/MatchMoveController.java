package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import top.dtc.addon.integration.match_move.domain.WebhookPayloadTransferCreditInResult;
import top.dtc.common.json.JSON;
import top.dtc.settlement.module.match_move.service.MatchMoveProcessService;

@Log4j2
@RestController
public class MatchMoveController {

    @Autowired
    MatchMoveProcessService matchMoveProcessService;


    @PostMapping("/notify")
    public void notify(@RequestBody WebhookPayloadTransferCreditInResult webhookPayloadTransferCreditInResult) {
        log.debug("[POST] /notify {}", JSON.stringify(webhookPayloadTransferCreditInResult, true));
        matchMoveProcessService.notify(webhookPayloadTransferCreditInResult);
    }

}
