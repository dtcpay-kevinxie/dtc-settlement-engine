package top.dtc.settlement.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.data.core.enums.OtcStatus;
import top.dtc.data.core.enums.OtcType;
import top.dtc.data.core.model.Otc;
import top.dtc.data.core.service.OtcService;
import top.dtc.data.finance.enums.InvoiceType;
import top.dtc.data.finance.enums.PayableStatus;
import top.dtc.data.finance.enums.ReceivableStatus;
import top.dtc.data.finance.model.Payable;
import top.dtc.data.finance.model.PayableSub;
import top.dtc.data.finance.model.Receivable;
import top.dtc.data.finance.model.RemitInfo;
import top.dtc.data.finance.service.PayableService;
import top.dtc.data.finance.service.PayableSubService;
import top.dtc.data.finance.service.ReceivableService;
import top.dtc.data.finance.service.RemitInfoService;
import top.dtc.data.risk.enums.RiskLevel;
import top.dtc.data.risk.model.KycNonIndividual;
import top.dtc.data.risk.model.KycWalletAddress;
import top.dtc.data.risk.model.RiskMatrix;
import top.dtc.data.risk.service.KycNonIndividualService;
import top.dtc.data.risk.service.KycWalletAddressService;
import top.dtc.data.risk.service.RiskMatrixService;

import java.time.LocalDate;
import java.util.List;

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
    private PayableService payableService;

    @Autowired
    private ReceivableService receivableService;

    public void scheduled() {
        List<Otc> otcList = otcService.getByStatus(OtcStatus.AGREED);
        otcList.forEach(otc -> {
            RiskMatrix riskMatrix = riskMatrixService.getOneByClientIdAndClientType(otc.clientId, otc.clientType);
            if (riskMatrix.riskLevel == RiskLevel.SEVERE || riskMatrix.riskLevel == RiskLevel.HIGH) {
                //TODO : Send email to R&C Team
                return;
            }
            Long payableId = payableSubService.getOnePayableIdBySubIdAndType(otc.id, InvoiceType.OTC);
            Payable payable;
            Receivable receivable;
            if (payableId == null) {
                KycWalletAddress recipientAddress = kycWalletAddressService.getById(otc.recipientAddressId);
                KycNonIndividual kycNonIndividual = kycNonIndividualService.getById(otc.clientId);

                payable = new Payable();
                payable.status = PayableStatus.UNPAID;
                payable.type = InvoiceType.OTC;
                payable.beneficiary = kycNonIndividual.registerName;

                receivable = new Receivable();
                receivable.status = ReceivableStatus.NOT_RECEIVED;
                receivable.type = InvoiceType.OTC;
                receivable.payer = kycNonIndividual.registerName;

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
                }
                payableService.save(payable);
                receivableService.save(receivable);
                PayableSub payableSub = new PayableSub();
                payableSub.payableId = payable.id;
                payableSub.type = InvoiceType.OTC;
                payableSub.subId = otc.id;
                payableSubService.save(payableSub);
            }

        });

    }

    public void fundReceived() {

    }

    public void orderCompleted() {

    }

}
