package top.dtc.settlement.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.common.util.NotificationSender;
import top.dtc.data.finance.enums.PayableStatus;
import top.dtc.data.finance.model.Payable;
import top.dtc.data.finance.service.PayableService;
import top.dtc.settlement.constant.NotificationConstant;
import top.dtc.settlement.core.properties.NotificationProperties;

import java.time.LocalDate;
import java.util.Map;

@Log4j2
@Service
public class PayableProcessService {

    @Autowired
    private PayableService payableService;

    @Autowired
    NotificationProperties notificationProperties;

    public void writeOff(Payable originalPayable, String remark, String referenceNo) {
        originalPayable.referenceNo = referenceNo;
        originalPayable.remark = remark;
        originalPayable.status = PayableStatus.PAID;
        originalPayable.writeOffDate = LocalDate.now();
        payableService.updateById(originalPayable);
        try {
            String feeDetail = (originalPayable.txnFee != null && originalPayable.feeCurrency != null) ? String.format(" (fee applied: %s %s)", originalPayable.txnFee, originalPayable.feeCurrency) : "";
            NotificationSender.
                    by(NotificationConstant.NAMES.PAYABLE_WRITE_OFF)
                    .to(notificationProperties.financeRecipient)
                    .dataMap(Map.of("id", originalPayable.id + "",
                            "beneficiary", originalPayable.beneficiary,
                            "amount", originalPayable.amount + " " + originalPayable.currency + feeDetail,
                            "reference_no", originalPayable.referenceNo,
                            "status", originalPayable.status.desc,
                            "payable_url", notificationProperties.portalUrlPrefix + "/accounting/payable-info/" + originalPayable.id
                    ))
                    .send();
        } catch (Exception e) {
            log.error("Notification Error", e);
        }
    }

}
