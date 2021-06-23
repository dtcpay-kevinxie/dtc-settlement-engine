package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
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

    @GetMapping("/scheduled-pending-checker")
    public String scheduledPendingChecker() {
        cryptoTransactionProcessService.scheduledStatusChecker();
        return "SUCCESS";
    }

    @PostMapping("/notify")
    public void notify(@RequestBody TransactionResult transactionResult) {
        cryptoTransactionProcessService.notify(transactionResult);
    }

}
