package top.dtc.settlement.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.common.enums.ReserveStatus;
import top.dtc.data.settlement.model.Reserve;
import top.dtc.data.settlement.model.ReserveConfig;
import top.dtc.data.settlement.model.Settlement;
import top.dtc.data.settlement.service.ReserveConfigService;
import top.dtc.data.settlement.service.ReserveService;
import top.dtc.settlement.constant.ErrorMessage;
import top.dtc.settlement.exception.ReserveException;

@Log4j2
@Service
public class ReserveProcessService {

    @Autowired
    private ReserveService reserveService;

    @Autowired
    private ReserveConfigService reserveConfigService;

    public void calculateReserve(Settlement settlement) {
        Reserve reserve;
        ReserveConfig reserveConfig = reserveConfigService.getFirstByMerchantIdAndCurrency(settlement.merchantId, settlement.currency);
        if (settlement.reserveId == null) {
            // Create new Reserve
            reserve = new Reserve();
            reserve.type = reserveConfig.type;
            reserve.status = ReserveStatus.PENDING;
            reserve.reserveSettlementId = settlement.id;
            reserve.currency = settlement.currency;
            reserve.merchantId = settlement.merchantId;
            reserve.merchantName = settlement.merchantName;
            reserve.reservePeriod = reserveConfig.reservePeriod;
            reserve.reservedDate = settlement.settleDate;
            reserve.dateToRelease = reserve.reservedDate.plusDays(180);
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
                if (reserveConfig.reservePercentage == null) {
                    throw new ReserveException(ErrorMessage.RESERVE.INVALID_CONFIG);
                }
                reserve.reserveRate = reserveConfig.reservePercentage;
                reserve.totalAmount = settlement.saleAmount.multiply(reserveConfig.reservePercentage);
                break;
            case FIXED:
                if (reserveConfig.reserveAmount == null) {
                    throw new ReserveException(ErrorMessage.RESERVE.INVALID_CONFIG);
                }
                reserve.totalAmount = reserveConfig.reserveAmount;
                break;
            default:
                throw new ReserveException(ErrorMessage.RESERVE.INVALID_CONFIG);
        }
        reserveService.saveOrUpdate(reserve);
        settlement.reserveAmount = reserve.totalAmount;
        settlement.reserveId = reserve.id;
    }

}