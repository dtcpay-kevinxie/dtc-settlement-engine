package top.dtc.settlement.report_processor.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.addon.integration.notification.NotificationEngineClient;
import top.dtc.common.enums.Brand;
import top.dtc.common.exception.ValidationException;
import top.dtc.data.core.model.PaymentTransaction;
import top.dtc.data.core.service.BinInfoService;
import top.dtc.data.core.service.CountryService;
import top.dtc.data.core.service.PaymentTransactionService;
import top.dtc.data.finance.model.PaymentFeeStructure;
import top.dtc.data.finance.model.RemitInfo;
import top.dtc.data.finance.model.Reserve;
import top.dtc.data.finance.model.Settlement;
import top.dtc.data.finance.service.*;
import top.dtc.data.risk.model.KycWalletAddress;
import top.dtc.data.risk.service.KycWalletAddressService;
import top.dtc.data.wallet.enums.WalletStatus;
import top.dtc.data.wallet.model.WalletAccount;
import top.dtc.data.wallet.service.WalletAccountService;
import top.dtc.settlement.constant.NotificationConstant;
import top.dtc.settlement.report_processor.SettleReportXlsxProcessor;
import top.dtc.settlement.report_processor.vo.SettlementTransactionReport;
import top.dtc.settlement.service.CommonValidationService;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Log4j2
@Service
public class SettlementReportService {

    @Autowired
    CommonValidationService commonValidationService;

    @Autowired
    private PayoutReconcileService payoutReconcileService;

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private ReserveService reserveService;

    @Autowired
    private SettlementConfigService settlementConfigService;

    @Autowired
    private CountryService countryService;

    @Autowired
    private PaymentTransactionService paymentTransactionService;

    @Autowired
    NotificationEngineClient notificationEngineClient;

    @Autowired
    PaymentFeeStructureService paymentFeeStructureService;

    @Autowired
    BinInfoService binInfoService;

    @Autowired
    RemitInfoService remitInfoService;

    @Autowired
    WalletAccountService walletAccountService;

    @Autowired
    KycWalletAddressService kycWalletAddressService;

    public void sendSettlementReportToMerchant(Long settlementId) {
        Settlement settlement = settlementService.getById(settlementId);
        if (settlement == null) {
            throw new ValidationException("Invalid Settlement Id");
        }
        String settleTo;
        switch (settlement.recipientAccountType) {
            case BANK -> {
                RemitInfo recipient = remitInfoService.getById(settlement.recipientAccountId);
                if (recipient == null || recipient.ownerId.equals(settlement.merchantId)) {
                    log.error("Invalid Remit Info");
                    return;
                }
                settleTo = String.format("%s [%s %s]", recipient.beneficiaryBankName, recipient.beneficiaryAccount, recipient.beneficiaryName);
            }
            case DTC_WALLET -> {
                WalletAccount walletAccount = walletAccountService.getById(settlement.recipientAccountId);
                if (walletAccount == null || walletAccount.status != WalletStatus.ACTIVE || walletAccount.currency != settlement.currency) {
                    log.error("Invalid Wallet Account");
                    throw new ValidationException("Invalid Wallet Account");
                }
                BigDecimal originalBalance = walletAccount.balance;
                settleTo = String.format("DTC Wallet %s Account, balance: %s -> %s", walletAccount.currency, originalBalance, walletAccount.balance);
            }
            case CRYPTO -> {
                KycWalletAddress recipientWallet = kycWalletAddressService.getById(settlement.recipientAccountId);
                settleTo = String.format("%s Wallet Address %s", recipientWallet.mainNet.desc, recipientWallet.address);
            }
            default -> throw new ValidationException("Invalid Recipient Account Type");
        }
        List<String> recipients = commonValidationService.getClientUserEmails(settlement.merchantId);
        try {
            notificationEngineClient
                    .by(NotificationConstant.NAMES.SETTLEMENT_APPROVED)
                    .to(recipients)
                    .dataMap(Map.of(
                            "client_name", commonValidationService.getClientName(settlement.merchantId),
                            "invoice_number", settlement.invoiceNumber,
                            "amount", settlement.settleFinalAmount.toString(),
                            "currency", settlement.currency.name,
                            "settle_to", settleTo
                    ))
                    .attachment(settlement.invoiceNumber + ".xlsx", genSettlementReport(settlement).toByteArray())
                    .send();
        } catch (Exception e) {
            log.error("Notification Error", e);
        }
    }

    public void sendSettlementReport(Long settlementId, String recipientEmail) {
        Settlement settlement = settlementService.getById(settlementId);
        if (settlement == null) {
            throw new ValidationException("Invalid Settlement Id");
        }
        try {
            notificationEngineClient
                    .by(NotificationConstant.NAMES.SETTLEMENT_REPORT)
                    .to(recipientEmail)
                    .dataMap(Map.of(
                            "client_name", commonValidationService.getClientName(settlement.merchantId),
                            "invoice_number", settlement.invoiceNumber
                    ))
                    .attachment(settlement.invoiceNumber + ".xlsx", genSettlementReport(settlement).toByteArray())
                    .send();
        } catch (Exception e) {
            log.error("Notification Error", e);
        }

    }

    public SettleReportXlsxProcessor genSettlementReport(Settlement settlement) throws IOException, IllegalAccessException {
        Reserve reserve = null;
        if (settlement.reserveId != null) {
            reserve = reserveService.getById(settlement.reserveId);
        }
        List<Long> transactionIds = payoutReconcileService.getTransactionIdBySettlementId(settlement.id);
        if (transactionIds == null || transactionIds.isEmpty()) {
            throw new ValidationException("No Transactions found");
        }
        HashMap<Brand, List<PaymentFeeStructure>> feeStructureMap
                = settlementConfigService.getByParams(settlement.merchantId, null, settlement.currency)
                .stream()
                .collect(Collectors.toMap(
                        o -> o.brand,
                        x -> paymentFeeStructureService.getBySettlementConfigId(x.id),
                        (left, right) -> {
                            left.addAll(right);
                            return left;
                        },
                        HashMap::new
                ));
        HashMap<Brand, List<SettlementTransactionReport>> transactionMap
                = transactionIds.stream()
                .map(transactionId -> {
                    PaymentTransaction paymentTransaction = paymentTransactionService.getById(transactionId);
                    SettlementTransactionReport settlementTransactionReport = new SettlementTransactionReport();
                    BeanUtils.copyProperties(paymentTransaction, settlementTransactionReport);
                    return settlementTransactionReport;
                })
                .toList()
                .stream()
                .collect(Collectors.toMap(
                        o -> o.brand,
                        x -> {
                            List<SettlementTransactionReport> list = new ArrayList<>();
                            list.add(x);
                            return list;
                        },
                        (left, right) -> {
                            left.addAll(right);
                            return left;
                        },
                        HashMap::new
                ));
        return SettleReportXlsxProcessor.build(
                settlement,
                reserve,
                transactionMap,
                feeStructureMap,
                countryService.getFirstByAlpha3("SGP")
        );
    }

}
