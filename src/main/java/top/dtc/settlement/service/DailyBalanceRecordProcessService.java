package top.dtc.settlement.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.data.finance.model.DailyBalanceRecord;
import top.dtc.data.finance.service.DailyBalanceRecordService;
import top.dtc.data.wallet.model.WalletAccount;
import top.dtc.data.wallet.service.WalletAccountService;
import top.dtc.settlement.core.properties.NotificationProperties;

import java.time.LocalDate;
import java.util.List;

@Log4j2
@Service
public class DailyBalanceRecordProcessService {

    @Autowired
    private WalletAccountService walletAccountService;

    @Autowired
    private DailyBalanceRecordService dailyBalanceRecordService;

    @Autowired
    NotificationProperties notificationProperties;

    public void processDayEndBalance() {
        List<WalletAccount> walletAccountList = walletAccountService.getByParams(null, null, null, null, null);
        walletAccountList.forEach(walletAccount -> {
            DailyBalanceRecord dailyBalanceRecord = new DailyBalanceRecord();
            dailyBalanceRecord.balanceDate = LocalDate.now().minusDays(1);
            dailyBalanceRecord.amount = walletAccount.balance;
            dailyBalanceRecord.currency = walletAccount.currency;
            dailyBalanceRecord.clientId = walletAccount.clientId;
            dailyBalanceRecordService.updateById(dailyBalanceRecord);
        });
    }

}
