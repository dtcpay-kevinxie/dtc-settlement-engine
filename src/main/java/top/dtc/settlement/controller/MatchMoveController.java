package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.dtc.common.json.JSON;
import top.dtc.settlement.module.matchmove.model.PaymentCallback;
import top.dtc.settlement.module.matchmove.service.MatchMoveService;

@Log4j2
@RestController
@RequestMapping("/match_move")
public class MatchMoveController {

    @Autowired
    MatchMoveService matchMoveService;

    @PostMapping("/notify")
    public void notify(@RequestBody PaymentCallback paymentCallback) {
        log.debug("[POST] /notify {}", JSON.stringify(paymentCallback, true));
        matchMoveService.notify(paymentCallback);
    }

}
