package top.dtc.settlement.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.common.enums.Currency;
import top.dtc.data.core.enums.ExchangeType;
import top.dtc.data.core.service.ExchangeRateService;
import top.dtc.data.finance.model.DailyBalanceRecord;
import top.dtc.data.finance.service.DailyBalanceRecordService;
import top.dtc.data.wallet.model.WalletAccount;
import top.dtc.data.wallet.service.WalletAccountService;
import top.dtc.settlement.core.properties.NotificationProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Log4j2
@Service
public class DailyBalanceRecordProcessService {

    @Autowired
    private WalletAccountService walletAccountService;

    @Autowired
    private ExchangeRateService exchangeRateService;

    @Autowired
    private DailyBalanceRecordService dailyBalanceRecordService;

    @Autowired
    NotificationProperties notificationProperties;

    public String processDayEndBalance() {
        BigDecimal rateUSDToSGD = exchangeRateService.getRate(Currency.USD, Currency.SGD, ExchangeType.RATE);
        BigDecimal rateUSDTToSGD = exchangeRateService.getRate(Currency.USDT, Currency.USD, ExchangeType.RATE).multiply(rateUSDToSGD);
        BigDecimal rateUSDCToSGD = exchangeRateService.getRate(Currency.USDC, Currency.USD, ExchangeType.RATE).multiply(rateUSDToSGD);
        BigDecimal rateBTCToSGD = exchangeRateService.getRate(Currency.BTC, Currency.USD, ExchangeType.RATE).multiply(rateUSDToSGD);
        BigDecimal rateETHToSGD = exchangeRateService.getRate(Currency.ETH, Currency.USD, ExchangeType.RATE).multiply(rateUSDToSGD);
        BigDecimal rateTRXToSGD = exchangeRateService.getRate(Currency.TRX, Currency.USD, ExchangeType.RATE).multiply(rateUSDToSGD);
        LocalDate yesterday = LocalDate.now();
        List<WalletAccount> walletAccountList = walletAccountService.getByParams(null, null, null, null, null);
        walletAccountList.forEach(walletAccount -> {
            DailyBalanceRecord dailyBalanceRecord = dailyBalanceRecordService.getOneByClientIdAndCurrencyAndBalanceDate(
                    walletAccount.clientId, walletAccount.currency, yesterday);
            if (dailyBalanceRecord == null) {
                dailyBalanceRecord = new DailyBalanceRecord();
            } else {
                return;
            }
            dailyBalanceRecord.balanceDate = yesterday;
            dailyBalanceRecord.amount = walletAccount.balance;
            dailyBalanceRecord.currency = walletAccount.currency;
            dailyBalanceRecord.clientId = walletAccount.clientId;
            switch (walletAccount.currency) {
                case SGD  -> dailyBalanceRecord.rateToSgd = BigDecimal.ONE;
                case USD  -> dailyBalanceRecord.rateToSgd = rateUSDToSGD;
                case BTC  -> dailyBalanceRecord.rateToSgd = rateBTCToSGD;
                case ETH  -> dailyBalanceRecord.rateToSgd = rateETHToSGD;
                case TRX  -> dailyBalanceRecord.rateToSgd = rateTRXToSGD;
                case USDT -> dailyBalanceRecord.rateToSgd = rateUSDTToSGD;
                case USDC -> dailyBalanceRecord.rateToSgd = rateUSDCToSGD;
                default -> log.error("Invalid Currency Account {}", walletAccount);
            }
            dailyBalanceRecordService.save(dailyBalanceRecord);
        });
        return null;
    }

}
