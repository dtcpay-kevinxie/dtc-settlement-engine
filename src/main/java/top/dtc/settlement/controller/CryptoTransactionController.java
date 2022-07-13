package top.dtc.settlement.controller;

import com.alibaba.fastjson.JSON;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dtc.common.model.crypto.CryptoTransactionResult;
import top.dtc.common.util.SchedulerUtils;
import top.dtc.settlement.service.CryptoTransactionProcessService;

@Log4j2
@RestController
@RequestMapping("/crypto-transaction")
public class CryptoTransactionController {

    @Autowired
    CryptoTransactionProcessService cryptoTransactionProcessService;

    @PostMapping("/scheduled/satoshi-pending-checker")
    public String scheduledPendingChecker(
            @RequestParam("group") String group,
            @RequestParam("name") String name,
            @RequestParam("async") boolean async
    ) {
        log.debug("[POST] /scheduled/satoshi-pending-checker");
        return SchedulerUtils.executeTask(group, name, async, () -> cryptoTransactionProcessService.scheduledStatusChecker());
    }

    @PostMapping("/scheduled/auto-sweep")
    public String scheduledSweep(
            @RequestParam("group") String group,
            @RequestParam("name") String name,
            @RequestParam("async") boolean async
    ) {
        log.debug("[POST] /scheduled/auto-sweep");
        return SchedulerUtils.executeTask(group, name, async, () -> cryptoTransactionProcessService.scheduledAutoSweep());
    }

    @PostMapping("/notify")
    public void notify(@RequestBody CryptoTransactionResult transactionResult) {
        log.debug("[POST] /notify {}", JSON.toJSONString(transactionResult, true));
        cryptoTransactionProcessService.notify(transactionResult);
    }

}
