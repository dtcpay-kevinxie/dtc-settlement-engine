package top.dtc.settlement.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.data.settlement.enums.AccountOwnerType;
import top.dtc.data.settlement.model.ClientAccount;
import top.dtc.data.settlement.model.Settlement;
import top.dtc.data.settlement.service.AdjustmentService;
import top.dtc.data.settlement.service.ClientAccountService;
import top.dtc.data.settlement.service.SettlementService;

import java.math.BigDecimal;
import java.util.List;

import static top.dtc.settlement.constant.SettlementConstant.NOT_SETTLED;

@Log4j2
@Service
public class ClientAccountProcessService {

    @Autowired
    private AdjustmentService adjustmentService;

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private ClientAccountService clientAccountService;

    public void calculateBalance(Long ownerId, AccountOwnerType accountOwnerType, String currency) {
        ClientAccount clientAccount = clientAccountService.getClientAccount(ownerId, accountOwnerType, currency);
        calculateBalance(clientAccount);
    }

    public void calculateBalance(ClientAccount clientAccount) {
        if (clientAccount == null) {
            return;
        }
        List<Settlement> settlementList = settlementService.getByMerchantIdAndCurrencyAndStatusIn(clientAccount.ownerId, clientAccount.currency, NOT_SETTLED);
        if (settlementList.size() > 0) {
            clientAccount.balance = settlementList.stream().map(Settlement::getSettleFinalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        } else {
            clientAccount.balance = BigDecimal.ZERO;
        }
        clientAccountService.updateById(clientAccount);
    }

}
