package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dtc.addon.integration.crypto_engine.domain.CryptoTransactionResult;
import top.dtc.addon.integration.scheduler.SchedulerEngineClient;
import top.dtc.common.json.JSON;
import top.dtc.settlement.service.CryptoTransactionProcessService;

@Log4j2
@RestController
@RequestMapping("/crypto-transaction")
public class CryptoTransactionController {

    @Autowired
    CryptoTransactionProcessService cryptoTransactionProcessService;

    @Autowired
    SchedulerEngineClient schedulerEngineClient;

    @PostMapping("/scheduled/auto-sweep")
    public String scheduledSweep(
            @RequestParam("group") String group,
            @RequestParam("name") String name,
            @RequestParam("async") boolean async
    ) {
        log.debug("[POST] /scheduled/auto-sweep");
        return schedulerEngineClient.executeTask(group, name, async, () ->
                cryptoTransactionProcessService.scheduledAutoSweep()
        );
    }

    @PostMapping("/scheduled/daily-balance-check")
    public String scheduledDailyBalanceCheck(
            @RequestParam("group") String group,
            @RequestParam("name") String name,
            @RequestParam("async") boolean async
    ) {
        log.debug("[POST] /scheduled/daily-balance-check");
        return schedulerEngineClient.executeTask(group, name, async, () ->
                cryptoTransactionProcessService.scheduledDtcWalletBalanceCheck()
        );
    }

    @PostMapping("/notify")
    public void notify(@RequestBody CryptoTransactionResult transactionResult) {
        log.debug("[POST] /notify {}", JSON.stringify(transactionResult, true));
        cryptoTransactionProcessService.notify(transactionResult);
    }

}
