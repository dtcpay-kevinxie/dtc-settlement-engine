package top.dtc.settlement.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.data.finance.model.BalanceHistory;
import top.dtc.data.finance.service.BalanceHistoryService;
import top.dtc.data.wallet.model.WalletAccount;
import top.dtc.data.wallet.service.WalletAccountService;
import top.dtc.settlement.core.properties.NotificationProperties;

import java.time.LocalDate;
import java.util.List;

@Log4j2
@Service
public class BalanceProcessService {

    @Autowired
    private WalletAccountService walletAccountService;

    @Autowired
    private BalanceHistoryService balanceHistoryService;

    @Autowired
    NotificationProperties notificationProperties;

    public void processDayEndBalance() {
        List<WalletAccount> walletAccountList = walletAccountService.getByParams(null, null, null, null, null);
        walletAccountList.forEach(walletAccount -> {
            BalanceHistory balanceHistory = new BalanceHistory();
            balanceHistory.balanceDate = LocalDate.now().minusDays(1);
            balanceHistory.amount = walletAccount.balance;
            balanceHistory.currency = walletAccount.currency;
            balanceHistory.clientId = walletAccount.clientId;
            balanceHistoryService.updateById(balanceHistory);
        });
    }

}
