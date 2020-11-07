package top.dtc.settlement.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.data.settlement.enums.ReserveStatus;
import top.dtc.data.settlement.model.Reserve;
import top.dtc.data.settlement.model.Settlement;
import top.dtc.data.settlement.model.SettlementConfig;
import top.dtc.data.settlement.service.ReserveService;
import top.dtc.settlement.constant.ErrorMessage;
import top.dtc.settlement.exception.ReserveException;

@Log4j2
@Service
public class ReserveProcessService {

    @Autowired
    private ReserveService reserveService;

    public void calculateReserve(Settlement settlement, SettlementConfig settlementConfig) {
        if (settlementConfig.reserveType == null) {
            return;
        }
        Reserve reserve;
        if (settlement.reserveId == null) {
            // Create new Reserve
            reserve = new Reserve();
            reserve.type = settlementConfig.reserveType;
            reserve.reservePeriod = settlementConfig.reservePeriod;
            reserve.status = ReserveStatus.PENDING;
            reserve.reserveSettlementId = settlement.id;
            reserve.currency = settlement.currency;
            reserve.merchantId = settlement.merchantId;
            reserve.merchantName = settlement.merchantName;
            reserve.reservedDate = settlement.settleDate;
            if (settlementConfig.reservePeriod > 0) {
                reserve.dateToRelease = reserve.reservedDate.plusDays(settlementConfig.reservePeriod);
            }
            settlement.reserveId = reserve.id;
        } else {
            // Reset existing Reserve
            reserve = reserveService.getById(settlement.reserveId);
            if (reserve == null) {
                throw new ReserveException(ErrorMessage.RESERVE.INVALID_RESERVE_ID(settlement.reserveId));
            }
            if (reserve.status != ReserveStatus.PENDING) {
                throw new ReserveException(ErrorMessage.RESERVE.RESET_FAILED(reserve.id, reserve.status.desc));
            }
        }
        switch (reserve.type) {
            case ROLLING:
                if (settlementConfig.reserveRate == null) {
                    throw new ReserveException(ErrorMessage.RESERVE.INVALID_CONFIG);
                }
                reserve.reserveRate = settlementConfig.reserveRate;
                reserve.totalAmount = settlement.saleAmount.multiply(settlementConfig.reserveRate);
                break;
            case FIXED:
                if (settlementConfig.reserveAmount == null) {
                    throw new ReserveException(ErrorMessage.RESERVE.INVALID_CONFIG);
                }
                reserve.totalAmount = settlementConfig.reserveAmount;
                break;
            default:
                throw new ReserveException(ErrorMessage.RESERVE.INVALID_CONFIG);
        }
        reserveService.saveOrUpdate(reserve);
        settlement.reserveAmount = reserve.totalAmount;
    }

}