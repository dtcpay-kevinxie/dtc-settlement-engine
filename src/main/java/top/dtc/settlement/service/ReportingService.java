package top.dtc.settlement.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.common.enums.*;
import top.dtc.common.util.NotificationSender;
import top.dtc.data.core.enums.ClientStatus;
import top.dtc.data.core.model.ExchangeRate;
import top.dtc.data.core.model.Individual;
import top.dtc.data.core.model.MonitoringMatrix;
import top.dtc.data.core.model.NonIndividual;
import top.dtc.data.core.service.*;
import top.dtc.data.finance.service.RemitInfoService;
import top.dtc.data.risk.enums.RiskLevel;
import top.dtc.data.risk.model.RiskMatrix;
import top.dtc.data.risk.service.RiskMatrixService;
import top.dtc.data.wallet.model.WalletBalanceHistory;
import top.dtc.data.wallet.service.WalletBalanceHistoryService;
import top.dtc.settlement.core.properties.NotificationProperties;
import top.dtc.settlement.report_processor.MasReportXlsxProcessor;
import top.dtc.settlement.report_processor.vo.FiatTransactionReport;
import top.dtc.settlement.report_processor.vo.PoboTransactionReport;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static top.dtc.settlement.constant.NotificationConstant.NAMES.MAS_REPORT;

@Log4j2
@Service
public class ReportingService {

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
        return ratesMap;
    }

    public void processMonthlyReport() {
        try {
            masReport2A(
                    LocalDate.parse("20220701", DateTimeFormatter.ofPattern("yyyyMMdd")),
                    LocalDate.now(),
                    null
            );
        } catch (Exception e) {
            log.error("Report Failed", e);
        }
    }

    private void masReport1A(LocalDate startDate, LocalDate endDate, HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap) throws IOException, IllegalAccessException {
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

    private void masReport1B(LocalDate startDate, LocalDate endDate) throws IOException, IllegalAccessException {
        List<RiskMatrix> highRiskList = getHighRiskList();
        byte[] reportByte = MasReportXlsxProcessor.generate1b(startDate, endDate, highRiskList).toByteArray();
        sendReportEmail("1B", startDate.toString(), endDate.toString(), reportByte);
    }

    public void masReport2A(LocalDate startDate, LocalDate endDate, HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap) throws IOException, IllegalAccessException {
        if (ratesMap == null) {
            ratesMap = getRatesMap(startDate, endDate);
        }
        List<PoboTransactionReport> poboTransactionList = getDomesticPoboList(startDate, endDate);
        List<FiatTransactionReport> fiatTransactionList = getDomesticFiatList(startDate, endDate);
        byte[] reportByte = MasReportXlsxProcessor.generate2a(
                startDate, endDate, fiatTransactionList, poboTransactionList, ratesMap).toByteArray();
        sendReportEmail("2A", startDate.toString(), endDate.toString(), reportByte);
    }

    private void masReport2B(LocalDate startDate, LocalDate endDate, HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap) throws IOException, IllegalAccessException {
        if (ratesMap == null) {
            ratesMap = getRatesMap(startDate, endDate);
        }
        List<PoboTransactionReport> poboTransactionList = getDomesticPoboList(startDate, endDate);
        List<FiatTransactionReport> fiatTransactionList = getDomesticFiatList(startDate, endDate);
        List<RiskMatrix> highRiskList = getHighRiskList();
        Set<Long> clientInSGP = getIndividualIdListInSGP();
        clientInSGP.addAll(getNonIndividualIdListInSGP());
        byte[] reportByte = MasReportXlsxProcessor.generate2b(
                startDate, endDate, fiatTransactionList, poboTransactionList, clientInSGP, highRiskList, ratesMap).toByteArray();
        sendReportEmail("2B", startDate.toString(), endDate.toString(), reportByte);
    }

    private void masReport3A(LocalDate startDate, LocalDate endDate, HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap) throws IOException, IllegalAccessException {
        if (ratesMap == null) {
            ratesMap = getRatesMap(startDate, endDate);
        }
        List<PoboTransactionReport> poboTransactionList = getCrossBorderPoboList(startDate, endDate);
        List<FiatTransactionReport> fiatTransactionList = getCrossBorderFiatList(startDate, endDate);
        byte[] reportByte = MasReportXlsxProcessor.generate3a(
                startDate, endDate, fiatTransactionList, poboTransactionList, ratesMap).toByteArray();
        sendReportEmail("3A", startDate.toString(), endDate.toString(), reportByte);
    }

    private void masReport3B(LocalDate startDate, LocalDate endDate, HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap) throws IOException, IllegalAccessException {
        if (ratesMap == null) {
            ratesMap = getRatesMap(startDate, endDate);
        }
        List<PoboTransactionReport> poboTransactionList = getCrossBorderPoboList(startDate, endDate);
        List<FiatTransactionReport> fiatTransactionList = getCrossBorderFiatList(startDate, endDate);
        Set<Long> clientInSGP = getIndividualIdListInSGP();
        clientInSGP.addAll(getNonIndividualIdListInSGP());
        Set<Long> fiClient = getFiIdList();
        List<RiskMatrix> highRiskList = getHighRiskList();
        byte[] reportByte = MasReportXlsxProcessor.generate3b(
                startDate, endDate, fiatTransactionList, poboTransactionList, clientInSGP, fiClient, highRiskList, ratesMap).toByteArray();
        sendReportEmail("3B", startDate.toString(), endDate.toString(), reportByte);
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

    private List<PoboTransactionReport> getDomesticPoboList(LocalDate startDate, LocalDate endDate) {
        return getPoboReportList(startDate, endDate).stream()
                .filter(poboTransactionReport -> "SGP".equals(poboTransactionReport.recipientCountry))
                .collect(Collectors.toList());
    }

    private List<PoboTransactionReport> getCrossBorderPoboList(LocalDate startDate, LocalDate endDate) {
        return getPoboReportList(startDate, endDate).stream()
                .filter(poboTransactionReport -> !"SGP".equals(poboTransactionReport.recipientCountry))
                .collect(Collectors.toList());
    }

    private List<PoboTransactionReport> getPoboReportList(LocalDate startDate, LocalDate endDate) {
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
                    return poboTransactionReport;
                })
                .collect(Collectors.toList());
    }

    private List<FiatTransactionReport> getDomesticFiatList(LocalDate startDate, LocalDate endDate) {
        return getFiatReportList(startDate, endDate).stream()
                .filter(fiatTransactionReport -> // For DEPOSIT fiat transaction, no originator info, only can differentiate by currency, SGD account in SGP, the rest outside SGP
                        fiatTransactionReport.type == FiatTransactionType.WITHDRAW && "SGP".equals(fiatTransactionReport.recipientCountry)
                                || fiatTransactionReport.type == FiatTransactionType.DEPOSIT && fiatTransactionReport.currency == Currency.SGD
                )
                .collect(Collectors.toList());
    }

    private List<FiatTransactionReport> getCrossBorderFiatList(LocalDate startDate, LocalDate endDate) {
        return getFiatReportList(startDate, endDate).stream()
                .filter(fiatTransactionReport -> // For DEPOSIT fiat transaction, no originator info, only can differentiate by currency, SGD account in SGP, the rest outside SGP
                    fiatTransactionReport.type == FiatTransactionType.WITHDRAW && !"SGP".equals(fiatTransactionReport.recipientCountry)
                            || fiatTransactionReport.type == FiatTransactionType.DEPOSIT && fiatTransactionReport.currency != Currency.SGD
                )
                .collect(Collectors.toList());
    }

    private List<FiatTransactionReport> getFiatReportList(LocalDate startDate, LocalDate endDate) {
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
                    return fiatTransactionReport;
                })
                .collect(Collectors.toList());
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
