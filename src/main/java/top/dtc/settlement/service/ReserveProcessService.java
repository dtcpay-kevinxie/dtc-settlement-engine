package top.dtc.settlement.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.common.enums.ReserveStatus;
import top.dtc.data.settlement.model.MerchantAccount;
import top.dtc.data.settlement.model.Reserve;
import top.dtc.data.settlement.model.Settlement;
import top.dtc.data.settlement.service.MerchantAccountService;
import top.dtc.data.settlement.service.ReserveService;
import top.dtc.settlement.constant.ErrorMessage;
import top.dtc.settlement.exception.ReserveException;

@Log4j2
@Service
public class ReserveProcessService {

    @Autowired
    private ReserveService reserveService;

    @Autowired
    private MerchantAccountService merchantAccountService;

    public void calculateReserve(Settlement settlement) {
        Reserve reserve;
        MerchantAccount merchantAccount =  merchantAccountService.getFirstByMerchantIdAndCurrency(settlement.merchantId, settlement.currency);
        if (settlement.reserveId == null) {
            // Create new Reserve
            reserve = new Reserve();
            reserve.type = merchantAccount.reserveType;
            reserve.reservePeriod = merchantAccount.reservePeriod;
            reserve.status = ReserveStatus.PENDING;
            reserve.reserveSettlementId = settlement.id;
            reserve.currency = settlement.currency;
            reserve.merchantId = settlement.merchantId;
            reserve.merchantName = settlement.merchantName;
            reserve.reservedDate = settlement.settleDate;
            if (merchantAccount.reservePeriod > 0) {
                reserve.dateToRelease = reserve.reservedDate.plusDays(merchantAccount.reservePeriod);
            }
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
                if (merchantAccount.reserveRate == null) {
                    throw new ReserveException(ErrorMessage.RESERVE.INVALID_CONFIG);
                }
                reserve.reserveRate = merchantAccount.reserveRate;
                reserve.totalAmount = settlement.saleAmount.multiply(merchantAccount.reserveRate);
                break;
            case FIXED:
                if (merchantAccount.reserveAmount == null) {
                    throw new ReserveException(ErrorMessage.RESERVE.INVALID_CONFIG);
                }
                reserve.totalAmount = merchantAccount.reserveAmount;
                break;
            default:
                throw new ReserveException(ErrorMessage.RESERVE.INVALID_CONFIG);
        }
        reserveService.saveOrUpdate(reserve);
        settlement.reserveAmount = reserve.totalAmount;
        settlement.reserveId = reserve.id;
    }

}