package top.dtc.settlement.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.dtc.common.service.CommonNotificationService;
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
import top.dtc.settlement.core.properties.NotificationProperties;
import top.dtc.settlement.exception.OtcException;
import top.dtc.settlement.exception.PayableException;
import top.dtc.settlement.exception.ReceivableException;
import top.dtc.settlement.module.etherscan.model.EtherscanErc20Event;
import top.dtc.settlement.module.etherscan.service.EtherscanService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static top.dtc.data.risk.enums.MainNet.ERC20;
import static top.dtc.settlement.constant.ErrorMessage.OTC.HIGH_RISK_OTC;
import static top.dtc.settlement.constant.ErrorMessage.PAYABLE.CANCEL_PAYABLE_ERROR;
import static top.dtc.settlement.constant.ErrorMessage.PAYABLE.OTC_NOT_RECEIVED;
import static top.dtc.settlement.constant.ErrorMessage.RECEIVABLE.CANCEL_RECEIVABLE_ERROR;

@Log4j2
@Service
public class OtcProcessService {

    @Autowired
    private NotificationProperties notificationProperties;

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
    private PayableProcessService payableProcessService;

    @Autowired
    private EtherscanService etherscanService;

    @Autowired
    private CommonNotificationService commonNotificationService;

    public void scheduledBlockchain() {
        // OTC waiting for receiving token
        List<Otc> waitingList = otcService.getByParams(
                OtcType.SELLING,
                OtcStatus.AGREED,
                null,
                null,
                null,
                null,
                null
        );
        // Add OTC waiting for paying token
        waitingList.addAll(
                otcService.getByParams(
                        OtcType.BUYING,
                        OtcStatus.RECEIVED,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );
        // Process looping for blockchain scanning
        Map<KycWalletAddress, List<OtcKey>> map = waitingList.stream()
                .map(otc -> {
                    KycWalletAddress recipient = kycWalletAddressService.getById(otc.recipientAddressId);
                    KycWalletAddress sender = kycWalletAddressService.getById(otc.senderAddressId);
                    return new OtcKey(otc, recipient, sender);
                })
                .collect(Collectors.toMap(
                otcKey -> otcKey.dtcOpsAddress,
                x -> {
                    List<OtcKey> list = new ArrayList<>();
                    list.add(x);
                    return list;
                },
                (left, right) -> {
                    left.addAll(right);
                    return left;
                },
                HashMap::new
        ));
        List<String> unexpectedList = new ArrayList<>();
        for (KycWalletAddress dtcOpsAddress : map.keySet()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            List<OtcKey> otcKeys = map.get(dtcOpsAddress);
            switch (dtcOpsAddress.mainNet) {
                case ERC20:
                    List<EtherscanErc20Event> txnList = etherscanService.checkNewTransactions(dtcOpsAddress);
                    if (txnList == null) {
                        continue;
                    }
                    log.debug("New txn found {}", txnList);
                    dtcOpsAddress.lastTxnBlock = processMatching(txnList, otcKeys, unexpectedList);
                    break;
                case BTC:
                    //TODO: Integrate with BTC explorer API
                    break;
                default:
                    break;
            }
            kycWalletAddressService.updateById(dtcOpsAddress);
        }
        log.debug("Unexpected List {}", String.join("\n", unexpectedList));
        if (unexpectedList.size() > 0) {
            commonNotificationService.send(
                    7,
                    notificationProperties.financeRecipient,
                    Map.of("transactions", String.join("\n", unexpectedList)
                    )
            );
        }

    }

    public boolean generateReceivableAndPayable(Long otcId) {
        return generateReceivableAndPayable(otcService.getById(otcId));
    }

    @Transactional
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

    @Transactional
    public Receivable writeOffOtcReceivable(Long receivalbeId, BigDecimal amount, String desc, String referenceNo) {
        Receivable receivable = receivableProcessService.writeOff(receivalbeId, amount, desc, referenceNo);
        if (ERC20.desc.equals(receivable.bankName)) {
            etherscanService.validateErc20Txn(amount, receivable.bankAccount, referenceNo);
        }
        if (receivable.status == ReceivableStatus.RECEIVED) {
            updateOtcStatus(receivable);
        }
        return receivable;
    }

    private void updateOtcStatus(Receivable receivable) {
        Long otcId = receivableSubService.getOneReceivableIdBySubIdAndType(receivable.id, InvoiceType.OTC);
        Otc otc = otcService.getById(otcId);
        if (!isOtcHighRisk(otc)) {
            otc.status = OtcStatus.RECEIVED;
            otc.receivedTime = LocalDateTime.now();
            otcService.updateById(otc);
            Payable payable = payableService.getPayableByOtcId(otcId);
            payable.payableDate = LocalDate.now(); //TODO: Payable Date should be same day if before 3PM NYT, +1 Day if after
            payableService.updateById(payable);
            commonNotificationService.send(
                    8,
                    notificationProperties.financeRecipient,
                    Map.of("transaction_details", receivable.receivedCurrency + " " + receivable.receivedAmount,
                            "account_info", receivable.bankName + " " + receivable.bankAccount,
                            "receivable_id", String.valueOf(receivable.id)
                    )
            );
        } else {
            // Throw Exception to interrupt Receivable write-off, Money will not send out
            throw new OtcException(HIGH_RISK_OTC);
        }
    }

    @Transactional
    public Payable writeOffOtcPayable(Long payableId, String remark, String referenceNo) {
        Payable payable = payableProcessService.writeOff(payableId, remark, referenceNo);
        if (payable.recipientAddressId != null) {
            KycWalletAddress recipientAddress = kycWalletAddressService.getById(payable.recipientAddressId);
            if (recipientAddress.mainNet == ERC20) {
                etherscanService.validateErc20Txn(payable.amount, recipientAddress.address, referenceNo);
            }
        }
        if (payable.status == PayableStatus.PAID) {
            updateOtcStatus(payable);
        }
        return payable;
    }

    private void updateOtcStatus(Payable payable) {
        Long otcId = payableSubService.getOtcIdByPayableIdAndType(payable.id);
        Otc otc = otcService.getById(otcId);
        if (!isOtcHighRisk(otc)) {
            if (otc.status != OtcStatus.RECEIVED) {
                throw new OtcException(OTC_NOT_RECEIVED(otcId));
            }
            otc.status = OtcStatus.COMPLETED;
            otc.completedTime = LocalDateTime.now();
            otcService.updateById(otc);
            KycNonIndividual kycNonIndividual = kycNonIndividualService.getById(otc.clientId);
            commonNotificationService.send(
                    6,
                    kycNonIndividual.email,
                    Map.of("client_name", payable.beneficiary,
                            "id", otc.id.toString(),
                            "order_detail", String.format("%s %s %s", otc.type.desc, otc.quantity, otc.item),
                            "price", otc.price.toString(),
                            "total_amount", otc.totalPrice.setScale(2, RoundingMode.HALF_UP).toString(),
                            "reference_no", payable.referenceNo
                    )
            );
        }
    }

    @Transactional
    public void cancelReceivableAndPayable(Otc otc) {
        Payable payable = payableService.getPayableByOtcId(otc.id);
        if (payable != null) {
            if (payable.status != PayableStatus.UNPAID) {
                throw new PayableException(CANCEL_PAYABLE_ERROR);
            }
            payable.status = PayableStatus.CANCELLED;
            payableService.updateById(payable);
        }

        Receivable receivable = receivableService.getReceivableByOtcId(otc.id);
        if (receivable != null) {
            if (receivable.status != ReceivableStatus.NOT_RECEIVED) {
                throw new ReceivableException(CANCEL_RECEIVABLE_ERROR);
            }
            receivable.status = ReceivableStatus.CANCELLED;
            receivableService.updateById(receivable);
        }
    }

    private boolean isOtcHighRisk(Otc otc) {
        RiskMatrix riskMatrix = riskMatrixService.getOneByClientIdAndClientType(otc.clientId, otc.clientType);
        boolean isHighRisk = riskMatrix.riskLevel == RiskLevel.SEVERE || riskMatrix.riskLevel == RiskLevel.HIGH;
        if (isHighRisk) {
            KycNonIndividual kycNonIndividual = kycNonIndividualService.getById(otc.clientId);
            commonNotificationService.send(
                    5,
                    notificationProperties.otcHighRiskRecipient,
                    Map.of("id", otc.id.toString(),
                            "client_id", otc.clientId.toString(),
                            "client_name", kycNonIndividual.registerName,
                            "risk_level", riskMatrix.riskLevel.desc)
            );
        }
        return isHighRisk;
    }

    private String processMatching(List<EtherscanErc20Event> ethereumTxnList, List<OtcKey> otcKeys, List<String> unexpectedList) {
        String lastBlockNumber = ethereumTxnList.stream().map(etherTxn -> etherTxn.blockNumber).max(Comparator.comparingLong(Long::parseLong)).get();
        List<OtcKey> detectedOtcList = ethereumTxnList
                .stream()
                .map(etherTxn -> {
                    BigDecimal amount = new BigDecimal(etherTxn.value).movePointLeft(Integer.parseInt(etherTxn.tokenDecimal));
                    OtcKey comparingOtc = new OtcKey(etherTxn.from, etherTxn.to, amount);
                    for (OtcKey otcKey : otcKeys) {
                        log.debug("Comparing {} with {}", otcKey, comparingOtc);
                        if (otcKey.equals(comparingOtc)) {
                            processDetectedOtc(otcKey.otc, etherTxn.hash);
                            otcKeys.remove(otcKey);
                            return otcKey;
                        }
                    }
                    String unexpectedTxn = String.format("From %s to %s, amount %s, txnHash %s, Date %s \n",
                            etherTxn.from,
                            etherTxn.to,
                            amount,
                            etherTxn.hash,
                            LocalDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(etherTxn.timeStamp)), ZoneId.of("GMT+08:00"))
                    );
                    unexpectedList.add(unexpectedTxn);
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return lastBlockNumber;
    }

    private void processDetectedOtc(Otc otc, String txnReferenceNo) {
        log.debug("Txn {} matched OTC {}", txnReferenceNo, otc);
        if (!isOtcHighRisk(otc)) {
            if (otc.type == OtcType.SELLING) {
                Long receivableId = receivableSubService.getOneReceivableIdBySubIdAndType(otc.id, InvoiceType.OTC);
                Receivable receivable = receivableProcessService.writeOff(receivableId, otc.totalPrice, "System Auto Write-off", txnReferenceNo);
                updateOtcStatus(receivable);
            } else {
                Long payableId = payableSubService.getOnePayableIdBySubIdAndType(otc.id, InvoiceType.OTC);
                Payable payable = payableProcessService.writeOff(payableId, "System Auto Write-off", txnReferenceNo);
                updateOtcStatus(payable);
            }
        } else {
            log.warn("High Risk Transaction Detected.");
        }
    }

    private boolean generateReceivableAndPayable(Otc otc, Receivable receivable, Payable payable) {
        if (otc.type == OtcType.BUYING) {
            // Receive fiat from client, otc.remitInfoId is DTC remit info id
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
            // Pay fiat to client, otc.remitInfoId is client remit info id
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

    @Data
    @AllArgsConstructor
    private static class OtcKey {

        public String recipientAddress;
        public String senderAddress;
        public BigDecimal amount;
        public Otc otc;
        public KycWalletAddress dtcOpsAddress;
        public KycWalletAddress clientAddress;
        public String txnHash;

        public OtcKey(String senderAddress, String recipientAddress, BigDecimal amount) {
            this.senderAddress = senderAddress;
            this.recipientAddress = recipientAddress;
            this.amount = amount;
        }

        public OtcKey(Otc otc, KycWalletAddress recipient, KycWalletAddress sender) {
            this.recipientAddress = recipient.address;
            this.senderAddress = sender.address;
            this.amount = otc.quantity;
            this.otc = otc;
            if (otc.type == OtcType.SELLING) {
                this.dtcOpsAddress = recipient;
                this.clientAddress = sender;
            } else {
                this.dtcOpsAddress = sender;
                this.clientAddress = recipient;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OtcKey key = (OtcKey) o;
            return Objects.equals(recipientAddress, key.recipientAddress) &&
                    Objects.equals(senderAddress, key.senderAddress) &&
                    Objects.equals(amount, key.amount);
        }

        @Override
        public int hashCode() {
            return Objects.hash(recipientAddress, senderAddress, amount);
        }
    }

}
