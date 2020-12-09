package top.dtc.settlement.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.common.service.CommonNotificationService;
import top.dtc.common.util.StringUtils;
import top.dtc.data.core.enums.OtcStatus;
import top.dtc.data.core.enums.OtcType;
import top.dtc.data.core.model.Otc;
import top.dtc.data.core.service.OtcService;
import top.dtc.data.finance.enums.InvoiceType;
import top.dtc.data.finance.enums.PayableStatus;
import top.dtc.data.finance.enums.ReceivableStatus;
import top.dtc.data.finance.model.*;
import top.dtc.data.finance.service.*;
import top.dtc.data.risk.enums.RiskLevel;
import top.dtc.data.risk.model.KycNonIndividual;
import top.dtc.data.risk.model.KycWalletAddress;
import top.dtc.data.risk.model.RiskMatrix;
import top.dtc.data.risk.service.KycNonIndividualService;
import top.dtc.data.risk.service.KycWalletAddressService;
import top.dtc.data.risk.service.RiskMatrixService;
import top.dtc.settlement.exception.OtcException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static top.dtc.settlement.constant.ErrorMessage.OTC.HIGH_RISK_OTC;
import static top.dtc.settlement.constant.ErrorMessage.PAYABLE.INVALID_PAYABLE_REF;
import static top.dtc.settlement.constant.ErrorMessage.PAYABLE.OTC_NOT_RECEIVED;

@Log4j2
@Service
public class OtcProcessService {

    @Autowired
    private OtcService otcService;

    @Autowired
    private KycWalletAddressService kycWalletAddressService;

    @Autowired
    private KycNonIndividualService kycNonIndividualService;

    @Autowired
    private RemitInfoService remitInfoService;

    @Autowired
    private RiskMatrixService riskMatrixService;

    @Autowired
    private PayableSubService payableSubService;

    @Autowired
    private ReceivableSubService receivableSubService;

    @Autowired
    private PayableService payableService;

    @Autowired
    private ReceivableService receivableService;

    @Autowired
    private ReceivableProcessService receivableProcessService;

    @Autowired
    private CommonNotificationService commonNotificationService;

    private boolean isOtcHighRisk(Otc otc) {
        RiskMatrix riskMatrix = riskMatrixService.getOneByClientIdAndClientType(otc.clientId, otc.clientType);
        boolean isHighRisk = riskMatrix.riskLevel == RiskLevel.SEVERE || riskMatrix.riskLevel == RiskLevel.HIGH;
        if (isHighRisk) {
            KycNonIndividual kycNonIndividual = kycNonIndividualService.getById(otc.clientId);
            commonNotificationService.send(
                    6,
                    "risk@dtc.top",
                    Map.of("id", otc.id.toString(),
                            "client_id", otc.clientId.toString(),
                            "client_name", kycNonIndividual.registerName,
                            "risk_level", riskMatrix.riskLevel.desc)
            );

        }
        return isHighRisk;
    }

    public void scheduled() {
        List<Otc> otcList = otcService.getByStatus(OtcStatus.AGREED);
        otcList.forEach(this::generateReceivableAndPayable);
    }

    public boolean generateReceivableAndPayable(Otc otc) {
        if (isOtcHighRisk(otc)) {
            return false;
        }
        Payable payable = payableService.getPayableByOtcId(otc.id);
        Receivable receivable = receivableService.getReceivableByOtcId(otc.id);
        if (payable == null) {
            KycNonIndividual kycNonIndividual = kycNonIndividualService.getById(otc.clientId);
            payable = new Payable();
            payable.status = PayableStatus.UNPAID;
            payable.type = InvoiceType.OTC;
            payable.beneficiary = kycNonIndividual.registerName;
            receivable = new Receivable();
            receivable.status = ReceivableStatus.NOT_RECEIVED;
            receivable.type = InvoiceType.OTC;
            receivable.payer = kycNonIndividual.registerName;
            if (generateReceivableAndPayable(otc, receivable, payable)) {
                linkOtc(otc, receivable, payable);
                return true;
            } else {
                return false;
            }
        } else {
            // Reset Payable and Receivable details
            return generateReceivableAndPayable(otc, receivable, payable);
        }
    }

    public Receivable writeOffOtcReceivable(Long receivalbeId, BigDecimal amount, String desc, String referenceNo) {
        Receivable receivable = receivableProcessService.writeOff(receivalbeId, amount, desc, referenceNo);
        if (receivable.status == ReceivableStatus.RECEIVED) {
            updateOtcStatus(receivable);
        }
        return receivable;
    }

    private boolean generateReceivableAndPayable(Otc otc, Receivable receivable, Payable payable) {
        if (otc.type == OtcType.BUYING) {
            // Receive fiat from client
            RemitInfo remitInfo = remitInfoService.getById(otc.remitInfoId);
            if (remitInfo == null) {
                log.error("Invalid RemitInfo {}", otc.remitInfoId);
                return false;
            }
            receivable.bankName = remitInfo.beneficiaryBankName;
            receivable.bankAccount = remitInfo.beneficiaryAccount;
            receivable.currency = otc.priceInCurrency;
            receivable.amount = otc.totalPrice;
            receivable.receivableDate = LocalDate.now().plusDays(1);
            // Pay crypto to client
            payable.currency = otc.item;
            payable.amount = otc.quantity;
            payable.recipientAddressId = otc.recipientAddressId;
        } else if (otc.type == OtcType.SELLING) {
            // Receive crypto from client
            KycWalletAddress recipientAddress = kycWalletAddressService.getById(otc.recipientAddressId);
            receivable.bankName = recipientAddress.mainNet.desc;
            receivable.bankAccount = recipientAddress.address;
            receivable.currency = otc.item;
            receivable.amount = otc.quantity;
            receivable.receivableDate = LocalDate.now();
            // Pay fiat to client
            payable.currency = otc.priceInCurrency;
            payable.amount = otc.totalPrice;
            payable.remitInfoId = otc.remitInfoId;
        } else {
            log.error("Invalid OtcType {}", otc.type);
            return false;
        }
        payableService.saveOrUpdate(payable);
        receivableService.saveOrUpdate(receivable);
        return true;
    }

    private void linkOtc(Otc otc, Receivable receivable, Payable payable) {
        PayableSub payableSub = new PayableSub();
        payableSub.payableId = payable.id;
        payableSub.type = InvoiceType.OTC;
        payableSub.subId = otc.id;
        payableSubService.save(payableSub);
        ReceivableSub receivableSub = new ReceivableSub();
        receivableSub.receivableId = receivable.id;
        receivableSub.type = InvoiceType.OTC;
        receivableSub.subId = otc.id;
        receivableSubService.save(receivableSub);
    }

    private void updateOtcStatus(Receivable receivable) {
        List<Long> otcIdList = receivableSubService.getSubIdByReceivableIdAndType(receivable.id, InvoiceType.OTC);
        otcIdList.forEach(otcId -> {
            Otc otc = otcService.getById(otcId);
            if (!isOtcHighRisk(otc)) {
                otc.status = OtcStatus.RECEIVED;
                otc.receivedTime = LocalDateTime.now();
                otcService.updateById(otc);
            } else {
                // Throw Exception to interrupt Receivable write-off, Money will not send out
                throw new OtcException(HIGH_RISK_OTC);
            }
        });
    }

    public Payable writeOffOtcPayable(Long payableId, String referenceNo) {
        if (StringUtils.isBlank(referenceNo)) {
            throw new OtcException(INVALID_PAYABLE_REF);
        }
        Payable payable = payableService.getById(payableId);
        Long otcId = payableSubService.getOtcIdByPayableIdAndType(payable.id);
        Otc otc = otcService.getById(otcId);
        if (!isOtcHighRisk(otc)) {
            if (otc.status != OtcStatus.RECEIVED) {
                throw new OtcException(OTC_NOT_RECEIVED(otcId));
            }
            otc.status = OtcStatus.COMPLETED;
            otc.completedTime = LocalDateTime.now();
            otcService.updateById(otc);
            //TODO : Send receipt email to Client and Ops
        }
        return payable;
    }

}
