package top.dtc.settlement.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.common.enums.SettlementStatus;
import top.dtc.common.util.StringUtils;
import top.dtc.data.core.service.TransactionService;
import top.dtc.data.finance.enums.InvoiceType;
import top.dtc.data.finance.enums.PayableStatus;
import top.dtc.data.finance.model.Payable;
import top.dtc.data.finance.model.Settlement;
import top.dtc.data.finance.service.PayableService;
import top.dtc.data.finance.service.PayoutReconcileService;
import top.dtc.data.finance.service.SettlementService;
import top.dtc.settlement.exception.PayableException;

import java.time.LocalDate;
import java.util.List;

import static top.dtc.settlement.constant.ErrorMessage.PAYABLE.INVALID_PAYABLE;
import static top.dtc.settlement.constant.ErrorMessage.PAYABLE.INVALID_PAYABLE_PARA;

@Log4j2
@Service
public class PayableProcessService {

    @Autowired
    private PayableService payableService;

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private PayoutReconcileService payoutReconcileService;

    @Autowired
    private TransactionService transactionService;

    public void createPayable(Payable payable) {
        payableService.save(payable);
    }

    public void editPayable(Payable payable) {
        payableService.updateById(payable);
    }

    public Payable writeOff(Long payableId, String remark, String referenceNo) {
        if (payableId == null || StringUtils.isBlank(referenceNo)) {
            throw new PayableException(INVALID_PAYABLE_PARA);
        }
        Payable payable = payableService.getById(payableId);
        if (payable == null || payable.type != InvoiceType.PAYMENT) {
            throw new PayableException(INVALID_PAYABLE);
        }
        payable.referenceNo = referenceNo;
        payable.remark = remark;
        payable.status = PayableStatus.PAID;
        payable.writeOffDate = LocalDate.now();
        payableService.updateById(payable);
        Settlement settlement = settlementService.getSettlementByPayableId(payable.id);
        settlement.status = SettlementStatus.PAID;
        List<Long> ids = payoutReconcileService.getTransactionIdBySettlementId(settlement.id);
        transactionService.updateSettlementStatusByIdIn(SettlementStatus.PAID, ids);
        return payable;
    }

}
