package top.dtc.settlement.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.common.enums.CryptoTransactionState;
import top.dtc.common.enums.CryptoTransactionType;
import top.dtc.data.core.model.CryptoTransaction;
import top.dtc.data.core.service.CryptoTransactionService;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Log4j2
public class CryptoTransactionProcessService {

    @Autowired
    CryptoTransactionService cryptoTransactionService;

    public void scheduledStatusChecker() {
        List<CryptoTransaction> list = cryptoTransactionService.list();
        list.forEach(k -> {
            if (k.state == CryptoTransactionState.PENDING
                    && k.type == CryptoTransactionType.SATOSHI
                    && k.requestTimestamp.isBefore(LocalDateTime.now().minusMinutes(30))
            ) {
                k.state = CryptoTransactionState.CLOSED;
                try {
                    cryptoTransactionService.updateById(k);
                } catch (Exception e) {
                    log.error("Update CryptoTransaction Failed", e);
                }
            }
        });
    }

}
