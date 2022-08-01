package top.dtc.settlement.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.common.enums.Currency;
import top.dtc.common.enums.*;
import top.dtc.common.util.ClientTypeUtils;
import top.dtc.common.util.NotificationSender;
import top.dtc.data.core.enums.ClientStatus;
import top.dtc.data.core.enums.OtcStatus;
import top.dtc.data.core.enums.TerminalStatus;
import top.dtc.data.core.model.*;
import top.dtc.data.core.service.*;
import top.dtc.data.finance.model.DailyBalanceRecord;
import top.dtc.data.finance.service.DailyBalanceRecordService;
import top.dtc.data.finance.service.RemitInfoService;
import top.dtc.data.risk.enums.RiskLevel;
import top.dtc.data.risk.model.RiskMatrix;
import top.dtc.data.risk.service.RiskMatrixService;
import top.dtc.data.wallet.enums.WalletStatus;
import top.dtc.data.wallet.model.WalletAccount;
import top.dtc.data.wallet.model.WalletBalanceHistory;
import top.dtc.data.wallet.service.WalletAccountService;
import top.dtc.data.wallet.service.WalletBalanceHistoryService;
import top.dtc.settlement.core.properties.NotificationProperties;
import top.dtc.settlement.report_processor.MasReportXlsxProcessor;
import top.dtc.settlement.report_processor.vo.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static top.dtc.settlement.constant.NotificationConstant.NAMES.MAS_REPORT;

@Log4j2
@Service
public class ReportService {

    @Autowired
    NotificationProperties notificationProperties;

    @Autowired
    WalletBalanceHistoryService walletBalanceHistoryService;

    @Autowired
    MonitoringMatrixService monitoringMatrixService;

    @Autowired
    RiskMatrixService riskMatrixService;

    @Autowired
    ExchangeRateService exchangeRateService;

    @Autowired
    PoboTransactionService poboTransactionService;

    @Autowired
    RemitInfoService remitInfoService;

    @Autowired
    FiatTransactionService fiatTransactionService;

    @Autowired
    IndividualService individualService;

    @Autowired
    NonIndividualService nonIndividualService;

    @Autowired
    PaymentTransactionService paymentTransactionService;

    @Autowired
    TerminalService terminalService;

    @Autowired
    OtcService otcService;

    @Autowired
    CryptoTransactionService cryptoTransactionService;

    @Autowired
    DailyBalanceRecordService dailyBalanceRecordService;

    @Autowired
    WalletAccountService walletAccountService;

    @Autowired
    CountryService countryService;

    public HashMap<LocalDate, HashMap<Currency, BigDecimal>> getRatesMap(LocalDate startDate, LocalDate endDate) {
        HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap = new HashMap<>();
        for (LocalDate rateDate = startDate; rateDate.isBefore(endDate); rateDate = rateDate.plusDays(1)) {
            HashMap<Currency, BigDecimal> dateRateToSGD = new HashMap<>();
            for (Currency sellCurrency : Currency.values()) {
                if (sellCurrency == Currency.SGD) {
                    dateRateToSGD.put(Currency.SGD, BigDecimal.ONE);
                    continue;
                }
                ExchangeRate exchangeRate = exchangeRateService.getRateByDate(sellCurrency, Currency.SGD, rateDate);
                if (exchangeRate != null) {
                    dateRateToSGD.put(sellCurrency, exchangeRate.exchangeRate);
                }
            }
            ratesMap.put(rateDate, dateRateToSGD);
        }
        log.debug("Rate MAP \n{}", ratesMap);
        return ratesMap;
    }

    public void processMonthlyReport(LocalDate startDate, LocalDate endDate) {
        HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap = getRatesMap(startDate, endDate);
        try {
            masReport1A(startDate, endDate, ratesMap);
        } catch (Exception e) {
            log.error("1A Report Failed", e);
        }
        try {
            masReport2A(startDate, endDate, ratesMap);
        } catch (Exception e) {
            log.error("2A Report Failed", e);
        }
        try {
            masReport3A(startDate, endDate, ratesMap);
        } catch (Exception e) {
            log.error("3A Report Failed", e);
        }
        try {
            masReport4A(startDate, endDate, ratesMap);
        } catch (Exception e) {
            log.error("4A Report Failed", e);
        }
        try {
            masReport5(startDate, endDate, ratesMap);
        } catch (Exception e) {
            log.error("5 Report Failed", e);
        }
        try {
            masReport6A(startDate, endDate, ratesMap);
        } catch (Exception e) {
            log.error("6A Report Failed", e);
        }
    }
    public void processHalfYearReport(LocalDate startDate, LocalDate endDate) {
        HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap = getRatesMap(startDate, endDate);
        try {
            masReport1B(startDate, endDate);
        } catch (Exception e) {
            log.error("1B Report Failed", e);
        }
        try {
            masReport2B(startDate, endDate, ratesMap);
        } catch (Exception e) {
            log.error("2B Report Failed", e);
        }
        try {
            masReport3B(startDate, endDate, ratesMap);
        } catch (Exception e) {
            log.error("3B Report Failed", e);
        }
        try {
            masReport4B(startDate, endDate, ratesMap);
        } catch (Exception e) {
            log.error("4B Report Failed", e);
        }
        try {
            masReport6B(startDate, endDate, ratesMap);
        } catch (Exception e) {
            log.error("6B Report Failed", e);
        }
    }
    public void processYearlyReport(LocalDate startDate, LocalDate endDate) {
        HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap = getRatesMap(startDate, endDate);
        //TODO: Annual Report

    }

    public void masReport1A(LocalDate startDate, LocalDate endDate, HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap) throws IOException, IllegalAccessException {
        if (ratesMap == null) {
            ratesMap = getRatesMap(startDate, endDate);
        }
        List<MonitoringMatrix> monitoringMatrixList = monitoringMatrixService.getByParams(
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        List<WalletBalanceHistory> walletBalanceHistoryList = walletBalanceHistoryService.getByParams(
                null,
                null,
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay()
        );
        byte[] reportByte = MasReportXlsxProcessor.generate1a(
                startDate, endDate, monitoringMatrixList, walletBalanceHistoryList, ratesMap).toByteArray();
        sendReportEmail("1A", startDate.toString(), endDate.toString(), reportByte);
    }

    public void masReport1B(LocalDate startDate, LocalDate endDate) throws IOException, IllegalAccessException {
        List<RiskMatrix> highRiskList = getHighRiskList();
        byte[] reportByte = MasReportXlsxProcessor.generate1b(startDate, endDate, highRiskList).toByteArray();
        sendReportEmail("1B", startDate.toString(), endDate.toString(), reportByte);
    }

    public void masReport2A(LocalDate startDate, LocalDate endDate, HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap) throws IOException, IllegalAccessException {
        if (ratesMap == null) {
            ratesMap = getRatesMap(startDate, endDate);
        }
        List<PoboTransactionReport> poboTransactionList = getDomesticPoboList(startDate, endDate, ratesMap);
        List<FiatTransactionReport> fiatTransactionList = getDomesticFiatList(startDate, endDate, ratesMap);
        byte[] reportByte = MasReportXlsxProcessor.generate2a(
                startDate, endDate, fiatTransactionList, poboTransactionList, ratesMap).toByteArray();
        sendReportEmail("2A", startDate.toString(), endDate.toString(), reportByte);
    }

    public void masReport2B(LocalDate startDate, LocalDate endDate, HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap) throws IOException, IllegalAccessException {
        if (ratesMap == null) {
            ratesMap = getRatesMap(startDate, endDate);
        }
        List<PoboTransactionReport> poboTransactionList = getDomesticPoboList(startDate, endDate, ratesMap);
        List<FiatTransactionReport> fiatTransactionList = getDomesticFiatList(startDate, endDate, ratesMap);
        List<RiskMatrix> highRiskList = getHighRiskList();
        Set<Long> clientInSGP = getIndividualIdListInSGP();
        clientInSGP.addAll(getNonIndividualIdListInSGP());
        byte[] reportByte = MasReportXlsxProcessor.generate2b(
                startDate, endDate, fiatTransactionList, poboTransactionList, clientInSGP, highRiskList, ratesMap).toByteArray();
        sendReportEmail("2B", startDate.toString(), endDate.toString(), reportByte);
    }

    public void masReport3A(LocalDate startDate, LocalDate endDate, HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap) throws IOException, IllegalAccessException {
        if (ratesMap == null) {
            ratesMap = getRatesMap(startDate, endDate);
        }
        List<PoboTransactionReport> poboTransactionList = getCrossBorderPoboList(startDate, endDate, ratesMap);
        List<FiatTransactionReport> fiatTransactionList = getCrossBorderFiatList(startDate, endDate, ratesMap);
        byte[] reportByte = MasReportXlsxProcessor.generate3a(
                startDate, endDate, fiatTransactionList, poboTransactionList, ratesMap).toByteArray();
        sendReportEmail("3A", startDate.toString(), endDate.toString(), reportByte);
    }

    public void masReport3B(LocalDate startDate, LocalDate endDate, HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap) throws IOException, IllegalAccessException {
        if (ratesMap == null) {
            ratesMap = getRatesMap(startDate, endDate);
        }
        List<PoboTransactionReport> poboTransactionList = getCrossBorderPoboList(startDate, endDate, ratesMap);
        List<FiatTransactionReport> fiatTransactionList = getCrossBorderFiatList(startDate, endDate, ratesMap);
        Set<Long> clientInSGP = getIndividualIdListInSGP();
        clientInSGP.addAll(getNonIndividualIdListInSGP());
        Set<Long> fiClient = getFiIdList();
        List<RiskMatrix> highRiskList = getHighRiskList();
        byte[] reportByte = MasReportXlsxProcessor.generate3b(
                startDate, endDate, fiatTransactionList, poboTransactionList, clientInSGP, fiClient, highRiskList, ratesMap).toByteArray();
        sendReportEmail("3B", startDate.toString(), endDate.toString(), reportByte);
    }

    public void masReport4A(LocalDate startDate, LocalDate endDate, HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap) throws IOException, IllegalAccessException {
        if (ratesMap == null) {
            ratesMap = getRatesMap(startDate, endDate);
        }
        List<PaymentTransactionReport> paymentTransactionList = getPaymentTransactionReportList(startDate, endDate, ratesMap);
        byte[] reportByte = MasReportXlsxProcessor.generate4a(
                startDate, endDate, paymentTransactionList, ratesMap).toByteArray();
        sendReportEmail("4A", startDate.toString(), endDate.toString(), reportByte);
    }

    public void masReport4B(LocalDate startDate, LocalDate endDate, HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap) throws IOException, IllegalAccessException {
        if (ratesMap == null) {
            ratesMap = getRatesMap(startDate, endDate);
        }
        List<PaymentTransactionReport> paymentTransactionList = getPaymentTransactionReportList(startDate, endDate, ratesMap);
        Set<Long> paymentClientIds = monitoringMatrixService.getByParams(
                        null, null, null, null, true, null, null)
                .stream().map(MonitoringMatrix::getClientId).collect(Collectors.toSet());
        List<NonIndividual> merchantList = nonIndividualService.getByParams(
                        null, null, null, null, null)
                .stream()
                .filter(nonIndividual -> paymentClientIds.contains(nonIndividual.id))
                .collect(Collectors.toList());
        List<Terminal> terminalList = terminalService.getByParams(null, null, null, TerminalStatus.ACTIVATED);
        byte[] reportByte = MasReportXlsxProcessor.generate4b(
                startDate, endDate, paymentTransactionList, merchantList, terminalList, ratesMap).toByteArray();
        sendReportEmail("4B", startDate.toString(), endDate.toString(), reportByte);
    }

    public void masReport5(LocalDate startDate, LocalDate endDate, HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap) throws IOException, IllegalAccessException {
        if (ratesMap == null) {
            ratesMap = getRatesMap(startDate, endDate);
        }
        byte[] reportByte = MasReportXlsxProcessor.generate5(startDate, endDate).toByteArray();
        sendReportEmail("5", startDate.toString(), endDate.toString(), reportByte);
    }

    public void masReport6A(LocalDate startDate, LocalDate endDate, HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap) throws IOException, IllegalAccessException {
        if (ratesMap == null) {
            ratesMap = getRatesMap(startDate, endDate);
        }
        List<OtcReport> otcList = getOtcReportList(startDate, endDate, ratesMap);
        byte[] reportByte = MasReportXlsxProcessor.generate6a(
                startDate, endDate, otcList, ratesMap).toByteArray();
        sendReportEmail("6A", startDate.toString(), endDate.toString(), reportByte);
    }

    public void masReport6B(LocalDate startDate, LocalDate endDate, HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap) throws IOException, IllegalAccessException {
        if (ratesMap == null) {
            ratesMap = getRatesMap(startDate, endDate);
        }
        List<OtcReport> otcList = getOtcReportList(startDate, endDate, ratesMap);
        List<CryptoTransactionReport> cryptoTransactionList = getCryptoTransactionReportList(startDate, endDate, ratesMap);
        Set<String> highRiskCountryList = countryService.getByParams(null, true, null)
                .stream().map(Country::getCodeAlpha3).collect(Collectors.toSet());
        Set<Long> dptClientInSGP = new HashSet<>();
        Set<Long> dptClientOutsideSGP = new HashSet<>();
        Set<Long> highRiskCountryClientIds = new HashSet<>();
        List<MonitoringMatrix> monitoringMatrixList = monitoringMatrixService.getByParams(
                null, null, null, true, null, null, null);
        for (MonitoringMatrix monitoringMatrix : monitoringMatrixList) {
            if (ClientTypeUtils.isIndividual(monitoringMatrix.clientId)) {
                Individual individual = individualService.getById(monitoringMatrix.clientId);
                if (individual == null || individual.status != ClientStatus.ACTIVATED) {
                    continue;
                }
                if ("SGP".equals(individual.country)) {
                    dptClientInSGP.add(individual.id);
                } else {
                    dptClientOutsideSGP.add(individual.id);
                }
                if (highRiskCountryList.contains(individual.country)) {
                    highRiskCountryClientIds.add(individual.id);
                }
            } else {
                NonIndividual nonIndividual = nonIndividualService.getById(monitoringMatrix.clientId);
                if (nonIndividual == null || nonIndividual.status != ClientStatus.ACTIVATED) {
                    continue;
                }
                if ("SGP".equals(nonIndividual.country)) {
                    dptClientInSGP.add(nonIndividual.id);
                } else {
                    dptClientOutsideSGP.add(nonIndividual.id);
                }
                if (highRiskCountryList.contains(nonIndividual.country)) {
                    highRiskCountryClientIds.add(nonIndividual.id);
                }
            }
        }
        // Get DPT enabled activated clients' daily balance record
        List<DailyBalanceRecord> dailyBalanceRecordList = dailyBalanceRecordService.getByParams(null, null, startDate, endDate).stream()
                .filter(dailyBalanceRecord -> dailyBalanceRecord.currency.isCrypto()
                        && (dptClientOutsideSGP.contains(dailyBalanceRecord.clientId) || dptClientInSGP.contains(dailyBalanceRecord.clientId)))
                .collect(Collectors.toList());
        // Get DPT enabled activated RiskMatrix
        List<RiskMatrix> riskMatrixList = riskMatrixService.list().stream()
                .filter(riskMatrix -> dptClientInSGP.contains(riskMatrix.clientId) || dptClientOutsideSGP.contains(riskMatrix.clientId))
                .collect(Collectors.toList());
        List<WalletAccount> cryptoAccountList = walletAccountService.getByParams(
                null,
                null,
                WalletStatus.ACTIVE,
                null,
                null
        ).stream()
                .filter(walletAccount -> walletAccount.currency.isCrypto()
                        && (dptClientInSGP.contains(walletAccount.clientId) || dptClientOutsideSGP.contains(walletAccount.clientId))) // DPT enabled activated Ids
                .collect(Collectors.toList());
        byte[] reportByte = MasReportXlsxProcessor.generate6b(
                startDate, endDate, otcList, cryptoTransactionList, dailyBalanceRecordList, riskMatrixList, dptClientInSGP, dptClientOutsideSGP, cryptoAccountList, highRiskCountryClientIds, ratesMap).toByteArray();
        sendReportEmail("6A", startDate.toString(), endDate.toString(), reportByte);
    }

    private List<RiskMatrix> getHighRiskList() {
        return riskMatrixService.getByParams(
                RiskLevel.HIGH,
                null,
                null,
                null,
                null,
                null
        );
    }

    private Set<Long> getIndividualIdListInSGP() {
        return individualService.getByParams(
                ClientStatus.ACTIVATED,
                null,
                "SGP",
                null,
                null,
                null
        ).stream()
                .map(Individual::getId)
                .collect(Collectors.toSet());
    }

    private Set<Long> getNonIndividualIdListInSGP() {
        return nonIndividualService.getByParams(
                ClientStatus.ACTIVATED,
                null,
                "SGP",
                null,
                null
        ).stream()
                .map(NonIndividual::getId)
                .collect(Collectors.toSet());
    }

    private Set<Long> getFiIdList() {
        return nonIndividualService.getByParams(
                ClientStatus.ACTIVATED,
                null,
                null,
                null,
                null
        ).stream()
                .filter(nonIndividual -> nonIndividual.type == ClientType.INSTITUTION)
                .map(NonIndividual::getId)
                .collect(Collectors.toSet());
    }

    private List<PoboTransactionReport> getDomesticPoboList(LocalDate startDate, LocalDate endDate, HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap) {
        return getPoboReportList(startDate, endDate, ratesMap).stream()
                .filter(poboTransactionReport -> "SGP".equals(poboTransactionReport.recipientCountry))
                .collect(Collectors.toList());
    }

    private List<PoboTransactionReport> getCrossBorderPoboList(LocalDate startDate, LocalDate endDate, HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap) {
        return getPoboReportList(startDate, endDate, ratesMap).stream()
                .filter(poboTransactionReport -> !"SGP".equals(poboTransactionReport.recipientCountry))
                .collect(Collectors.toList());
    }

    private List<PoboTransactionReport> getPoboReportList(LocalDate startDate, LocalDate endDate, HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap) {
        return poboTransactionService.getByParams(
                        null,
                        PoboTransactionState.COMPLETED,
                        null,
                        null,
                        null,
                        startDate.atStartOfDay(),
                        endDate.plusDays(1).atStartOfDay()
                ).stream()
                .filter(poboTransaction -> poboTransaction.recipientCurrency.isFiat())
                .map(poboTransaction -> {
                    PoboTransactionReport poboTransactionReport = new PoboTransactionReport();
                    BeanUtils.copyProperties(poboTransaction, poboTransactionReport);
                    poboTransactionReport.recipientCountry = remitInfoService.getById(poboTransactionReport.recipientAccountId).beneficiaryBankCountry;
                    poboTransactionReport.rateToSGD = ratesMap.get(poboTransactionReport.approvedTime.toLocalDate()).get(poboTransactionReport.recipientCurrency);
                    return poboTransactionReport;
                })
                .collect(Collectors.toList());
    }

    private List<FiatTransactionReport> getDomesticFiatList(LocalDate startDate, LocalDate endDate, HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap) {
        return getFiatReportList(startDate, endDate, ratesMap).stream()
                .filter(fiatTransactionReport -> // For DEPOSIT fiat transaction, no originator info, only can differentiate by currency, SGD account in SGP, the rest outside SGP
                        fiatTransactionReport.type == FiatTransactionType.WITHDRAW && "SGP".equals(fiatTransactionReport.recipientCountry)
                                || fiatTransactionReport.type == FiatTransactionType.DEPOSIT && fiatTransactionReport.currency == Currency.SGD
                )
                .collect(Collectors.toList());
    }

    private List<FiatTransactionReport> getCrossBorderFiatList(LocalDate startDate, LocalDate endDate, HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap) {
        return getFiatReportList(startDate, endDate, ratesMap).stream()
                .filter(fiatTransactionReport -> // For DEPOSIT fiat transaction, no originator info, only can differentiate by currency, SGD account in SGP, the rest outside SGP
                    fiatTransactionReport.type == FiatTransactionType.WITHDRAW && !"SGP".equals(fiatTransactionReport.recipientCountry)
                            || fiatTransactionReport.type == FiatTransactionType.DEPOSIT && fiatTransactionReport.currency != Currency.SGD
                )
                .collect(Collectors.toList());
    }

    private List<FiatTransactionReport> getFiatReportList(LocalDate startDate, LocalDate endDate, HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap) {
        return fiatTransactionService.getByParams(
                FiatTransactionState.COMPLETED,
                        null,
                        null,
                        null,
                        null,
                        startDate.atStartOfDay(),
                        endDate.plusDays(1).atStartOfDay()
                ).stream()
                .map(fiatTransaction -> {
                    FiatTransactionReport fiatTransactionReport = new FiatTransactionReport();
                    BeanUtils.copyProperties(fiatTransaction, fiatTransactionReport);
                    fiatTransactionReport.recipientCountry = remitInfoService.getById(fiatTransaction.remitInfoId).beneficiaryBankCountry;
                    fiatTransactionReport.rateToSGD = ratesMap.get(fiatTransactionReport.completedTime.toLocalDate()).get(fiatTransactionReport.currency);
                    return fiatTransactionReport;
                })
                .collect(Collectors.toList());
    }

    private List<PaymentTransactionReport> getPaymentTransactionReportList(LocalDate startDate, LocalDate endDate, HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap) {
        return paymentTransactionService.getByParams(
                null,
                PaymentTransactionState.SUCCESS,
                null,
                null,
                null,
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay(),
                null,
                null,
                null,
                null
        ).stream().map(paymentTransaction -> {
            PaymentTransactionReport paymentTransactionReport = new PaymentTransactionReport();
            BeanUtils.copyProperties(paymentTransaction, paymentTransactionReport);
            paymentTransactionReport.rateToSGD = ratesMap.get(paymentTransactionReport.dtcTimestamp.toLocalDate()).get(paymentTransactionReport.requestCurrency);
            return paymentTransactionReport;
        }).collect(Collectors.toList());
    }

    private List<OtcReport> getOtcReportList(LocalDate startDate, LocalDate endDate, HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap) {
        return otcService.getByParams(
                null,
                OtcStatus.COMPLETED,
                null,
                null,
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay()
        ).stream().map(otc -> {
            OtcReport otcReport = new OtcReport();
            BeanUtils.copyProperties(otc, otcReport);
            otcReport.rateToSGD = ratesMap.get(otcReport.completedTime.toLocalDate()).get(otcReport.fiatCurrency);
            return otcReport;
        }).collect(Collectors.toList());
    }

    private List<CryptoTransactionReport> getCryptoTransactionReportList(LocalDate startDate, LocalDate endDate, HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap) {
        return cryptoTransactionService.getByParams(
                null,
                CryptoTransactionState.COMPLETED,
                null,
                null,
                null,
                null,
                null,
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay()
        ).stream().map(cryptoTransaction -> {
            CryptoTransactionReport cryptoTransactionReport = new CryptoTransactionReport();
            BeanUtils.copyProperties(cryptoTransaction, cryptoTransactionReport);
            cryptoTransactionReport.rateToSGD = ratesMap.get(cryptoTransaction.lastUpdatedDate.toLocalDate()).get(cryptoTransaction.currency);
            return cryptoTransactionReport;
        }).collect(Collectors.toList());
    }

    private void sendReportEmail(String reportType, String startDate, String endDate, byte[] reportByte) {
        try {
            NotificationSender.
                    by(MAS_REPORT)
                    .to(notificationProperties.complianceRecipient)
                    .dataMap(Map.of("report_type", reportType,
                            "date_start", startDate,
                            "date_end", endDate
                    ))
                    .attachment("PSN04-Report-" + reportType + "-" + startDate + "-" + endDate + ".xlsx", reportByte)
                    .send();
        } catch (Exception e) {
            log.error("Notification Error", e);
        }
    }

}
