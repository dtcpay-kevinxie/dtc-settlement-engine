package top.dtc.settlement.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.common.exception.ValidationException;
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

import static top.dtc.settlement.constant.ErrorMessage.OTC.HIGH_RISK_OTC;
import static top.dtc.settlement.constant.ErrorMessage.PAYABLE.INVALID_PAYABLE_REF;
import static top.dtc.settlement.constant.ErrorMessage.PAYABLE.OTC_NOT_RECEIVED;
import static top.dtc.settlement.constant.ErrorMessage.RECEIVABLE.INVALID_RECEIVABLE_PARA;
import static top.dtc.settlement.constant.ErrorMessage.RECEIVABLE.INVALID_RECEIVABLE_STATUS;

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

    private boolean isOtcHighRisk(Otc otc) {
        RiskMatrix riskMatrix = riskMatrixService.getOneByClientIdAndClientType(otc.clientId, otc.clientType);
        return riskMatrix.riskLevel == RiskLevel.SEVERE || riskMatrix.riskLevel == RiskLevel.HIGH;
    }

    public void scheduled() {
        List<Otc> otcList = otcService.getByStatus(OtcStatus.AGREED);
        otcList.forEach(otc -> {
            if (isOtcHighRisk(otc)) {
                //TODO : Send email to R&C Team
                return;
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
                generateReceivableAndPayable(otc, receivable, payable);
                linkOtc(otc, receivable, payable);
            } else {
                // Reset Payable and Receivable details
                generateReceivableAndPayable(otc, receivable, payable);
            }
        });
    }

    private void generateReceivableAndPayable(Otc otc, Receivable receivable, Payable payable) {
        if (otc.type == OtcType.BUYING) {
            // Receive fiat from client
            RemitInfo remitInfo = remitInfoService.getById(otc.remitInfoId);
            if (remitInfo == null) {
                log.error("Invalid RemitInfo {}", otc.remitInfoId);
                return;
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
            return;
        }
        payableService.saveOrUpdate(payable);
        receivableService.saveOrUpdate(receivable);
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

    public void writeOffOtcReceivable(Long receivalbeId, BigDecimal amount, String desc, String referenceNo) {
        if (StringUtils.isBlank(referenceNo) || amount == null || receivalbeId == null) {
            throw new ValidationException(INVALID_RECEIVABLE_PARA);
        }
        Receivable receivable = receivableService.getById(receivalbeId);
        receivable.description = desc;
        switch (receivable.status) {
            case NOT_RECEIVED:
                receivable.referenceNo = referenceNo;
                receivable.receivedCurrency = receivable.currency;
                receivable.receivedAmount = amount;
                break;
            case PARTIAL:
                receivable.referenceNo += ";" + referenceNo;
                receivable.receivedAmount = receivable.receivedAmount.add(amount);
                break;
            default:
                throw new ValidationException(INVALID_RECEIVABLE_STATUS);
        }
        if (receivable.receivedAmount.compareTo(receivable.amount) >= 0) {
            receivable.status = ReceivableStatus.RECEIVED;
            List<Long> otcIdList = receivableSubService.getSubIdByReceivableIdAndType(receivable.id, InvoiceType.OTC);
            otcIdList.forEach(otcId -> {
                Otc otc = otcService.getById(otcId);
                if (!isOtcHighRisk(otc)) {
                    otc.status = OtcStatus.RECEIVED;
                    otc.receivedTime = LocalDateTime.now();
                    otcService.updateById(otc);
                }
            });
            receivable.writeOffDate = LocalDate.now();
        } else {
            receivable.status = ReceivableStatus.PARTIAL;
        }
        receivableService.updateById(receivable);
    }

    public void writeOffOtcPayable(Long payableId, String referenceNo) {
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
        } else {
            throw new OtcException(HIGH_RISK_OTC);
        }
    }

}
