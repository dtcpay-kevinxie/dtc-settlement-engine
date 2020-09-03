package top.dtc.settlement.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.common.enums.AdjustmentStatus;
import top.dtc.common.enums.SettlementStatus;
import top.dtc.data.settlement.model.Adjustment;
import top.dtc.data.settlement.model.Settlement;
import top.dtc.data.settlement.service.AdjustmentService;
import top.dtc.data.settlement.service.SettlementService;
import top.dtc.settlement.constant.ErrorMessage;
import top.dtc.settlement.exception.SettlementException;

@Log4j2
@Service
public class AdjustmentProcessService {

    @Autowired
    private AdjustmentService adjustmentService;

    @Autowired
    SettlementService settlementService;

    public void addAdjustment(Adjustment adjustment) {
        Settlement settlement = settlementService.getById(adjustment.settlementId);
        if (settlement == null || settlement.state != SettlementStatus.WAITING || adjustment.totalAmount == null) {
            throw new SettlementException(ErrorMessage.ADJUSTMENT.ADDING_FAILED + adjustment);
        }
        settlement.adjustmentAmount = settlement.adjustmentAmount.add(adjustment.totalAmount);
        settlement.settleFinalAmount = settlement.settleFinalAmount.add(adjustment.totalAmount);
        adjustment.status = AdjustmentStatus.PENDING;
        adjustmentService.save(adjustment);
        settlementService.updateById(settlement);
    }

    public void removeAdjustment(Long adjustmentId) {
        Adjustment adjustment = adjustmentService.getById(adjustmentId);
        if (adjustment != null) {
            Settlement settlement = settlementService.getById(adjustment.settlementId);
            if (settlement == null || settlement.state != SettlementStatus.WAITING) {
                throw new SettlementException(ErrorMessage.ADJUSTMENT.REMOVING_FAILED(adjustmentId, adjustment.settlementId));
            }
            settlement.adjustmentAmount = settlement.adjustmentAmount.subtract(adjustment.totalAmount);
            settlement.settleFinalAmount = settlement.settleFinalAmount.subtract(adjustment.totalAmount);
            settlementService.updateById(settlement);
            adjustmentService.removeById(adjustmentId);
        }
    }

}
