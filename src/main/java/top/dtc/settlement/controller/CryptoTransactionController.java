package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.dtc.settlement.service.CryptoTransactionProcessService;

/**
 * User: kevin.xie<br/>
 * Date: 20/05/2021<br/>
 * Time: 17:37<br/>
 */
@Log4j2
@RestController
@RequestMapping("/crypto-transaction")
public class CryptoTransactionController {

    @Autowired
    CryptoTransactionProcessService cryptoTransactionProcessService;

    @PostMapping("/scheduled/satoshi-pending-checker")
    public void scheduledPendingChecker() {
        try {
            log.debug("/scheduled/satoshi-pending-checker");
            cryptoTransactionProcessService.scheduledStatusChecker();
        } catch (Exception e) {
            log.error("Cannot process scheduled satoshi pending checker, {}", e.getMessage());
        }
    }

    @PostMapping("/notify")
    public void notify(@RequestBody TransactionResult transactionResult) {
        log.debug("crypto-transaction/notify {}", transactionResult);
        cryptoTransactionProcessService.notify(transactionResult);
    }

}
