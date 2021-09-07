//package top.dtc.settlement.service;
//
//import lombok.extern.log4j.Log4j2;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//import top.dtc.common.util.StringUtils;
//import top.dtc.data.finance.enums.PayableStatus;
//import top.dtc.data.finance.model.Payable;
//import top.dtc.data.finance.service.PayableService;
//import top.dtc.settlement.exception.PayableException;
//
//import java.time.LocalDate;
//
//import static top.dtc.settlement.constant.ErrorMessage.PAYABLE.INVALID_PAYABLE_PARA;
//
//@Log4j2
//@Service
//public class PayableProcessService {
//
//    @Autowired
//    private PayableService payableService;
//
//    public void createPayable(Payable payable) {
//        payableService.save(payable);
//    }
//
//    public void editPayable(Payable payable) {
//        payableService.updateById(payable);
//    }
//
//    public Payable writeOff(Long payableId, String remark, String referenceNo) {
//        if (payableId == null || StringUtils.isBlank(referenceNo)) {
//            throw new PayableException(INVALID_PAYABLE_PARA);
//        }
//        Payable payable = payableService.getById(payableId);
//        payable.referenceNo = referenceNo;
//        payable.remark = remark;
//        payable.status = PayableStatus.PAID;
//        payable.writeOffDate = LocalDate.now();
//        payableService.updateById(payable);
//        return payable;
//    }
//
//}
