package top.dtc.settlement.report_processor;

import com.google.common.base.Objects;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import top.dtc.common.enums.Currency;
import top.dtc.common.enums.FiatTransactionType;
import top.dtc.common.util.ClientTypeUtils;
import top.dtc.data.core.model.FiatTransaction;
import top.dtc.data.core.model.MonitoringMatrix;
import top.dtc.data.core.model.PoboTransaction;
import top.dtc.data.risk.model.RiskMatrix;
import top.dtc.data.wallet.model.WalletBalanceHistory;
import top.dtc.settlement.handler.FieldValue;
import top.dtc.settlement.handler.xlsx.XlsxGenerator;
import top.dtc.settlement.report_processor.vo.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.stream.Collectors;

import static java.math.BigDecimal.ZERO;

@Log4j2
public class MasReportXlsxProcessor {

    private XSSFWorkbook workbook = null;

    private static MasReportXlsxProcessor initReportWorkbook(String reportType) throws IOException {
        MasReportXlsxProcessor processor = new MasReportXlsxProcessor();
        processor.workbook = new XSSFWorkbook(
                MasReportXlsxProcessor.class.getResourceAsStream(String.format("/xlsx-templates/mas-report-%s.xlsx", reportType.toLowerCase(Locale.ROOT))));
        CellStyle percentCellStyle = processor.workbook.createCellStyle();
        percentCellStyle.setDataFormat(processor.workbook.createDataFormat().getFormat("0%"));
        return processor;
    }

    private static XSSFSheet initSummarySheet(MasReportXlsxProcessor processor, LocalDate startDate, LocalDate endDate, String reportType) {
        /*
                SHEET 0 Report 1A Summary
         */
        XSSFSheet sheet0 = processor.workbook.getSheetAt(0);
        XlsxGenerator.lock(sheet0);
        processor.workbook.setSheetName(0, String.format("%s Summary", reportType.toUpperCase(Locale.ROOT)));
        // 1A Title
        processor.getCellByPos(sheet0, "B2").setCellValue("Digital Treasures Center Pte. Ltd.");
        processor.getCellByPos(sheet0, "B3").setCellValue(String.format("%s to %s",
                startDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)), endDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))));
        return sheet0;
    }

    private static void generateAccountSheet(
            MasReportXlsxProcessor processor,
            List<MonitoringMatrix> monitoringMatrixList
    ) throws IllegalAccessException {
        /*
                SHEET Account Issued
         */
        XlsxGenerator
                .records(processor.workbook, monitoringMatrixList, AccountIssuedReport.class)
                .valueHandler((key, record) -> {
                    MonitoringMatrix monitoringMatrix = (MonitoringMatrix) record;
                    if ("isDomesticEnabled".equals(key)) {
                        return new FieldValue<>(monitoringMatrix.fiatAccountEnabled ? "YES" : "NO");
                    } else if ("isCrossBorderEnabled".equals(key)) {
                        return new FieldValue<>(monitoringMatrix.fiatAccountEnabled ? "YES" : "NO");
                    } else if ("isMerchantAcquisitionEnabled".equals(key)) {
                        return new FieldValue<>(monitoringMatrix.paymentEnabled ? "YES" : "NO");
                    } else if ("isEmoneyEnabled".equals(key)) {
                        return new FieldValue<>(monitoringMatrix.emoneyEnabled ? "YES" : "NO");
                    } else if ("isDptEnabled".equals(key)) {
                        return new FieldValue<>(monitoringMatrix.dptEnabled ? "YES" : "NO");
                    } else if ("isMoneyChangingEnabled".equals(key)) {
                        return new FieldValue<>("NO");
                    }
                    return FieldValue.empty();
                })
                .genSheet("Accounts Issued");
    }

    private static void generateFiatTransactionSheet(
            MasReportXlsxProcessor processor,
            List<FiatTransactionReport> fiatTransactionList,
            HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap
    ) throws IllegalAccessException {
        /*
               SHEET Fiat Transaction
         */
        XlsxGenerator
                .records(processor.workbook, fiatTransactionList, FiatTransactionReport.class)
                .valueHandler((key, record) -> {
                    FiatTransactionReport fiatTransactionReport = (FiatTransactionReport) record;
                    if ("rateToSGD".equals(key)) {
                        return new FieldValue<>(getRateToSGD(fiatTransactionReport.currency, fiatTransactionReport.completedTime, ratesMap));
                    }
                    return FieldValue.empty();
                })
                .genSheet("Fiat Transaction");
    }

    private static void generatePoboTransactionSheet(
            MasReportXlsxProcessor processor,
            List<PoboTransactionReport> poboTransactionList,
            HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap
    ) throws IllegalAccessException {
        /*
               SHEET Pobo Transaction
         */
        XlsxGenerator
                .records(processor.workbook, poboTransactionList, PoboTransactionReport.class)
                .valueHandler((key, record) -> {
                    PoboTransactionReport poboTransactionReport = (PoboTransactionReport) record;
                    if ("rateToSGD".equals(key)) {
                        return new FieldValue<>(getRateToSGD(poboTransactionReport.recipientCurrency, poboTransactionReport.approvedTime, ratesMap));
                    }
                    return FieldValue.empty();
                })
                .genSheet("Payment-on-behalf-of Transaction");
    }

    private static void generateRiskMatrixSheet(
            MasReportXlsxProcessor processor,
            List<RiskMatrix> riskMatrixList
    ) throws IllegalAccessException {
        /*
                SHEET Risk Matrix
         */
        XlsxGenerator
                .records(processor.workbook, riskMatrixList, RiskMatrixReport.class)
                .genSheet("Risk Matrix");
    }

    private static BigDecimal getRateToSGD(Currency currency, LocalDateTime txnTime, HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap) {
        return ratesMap.get(txnTime.toLocalDate()).get(currency);
    }

    private BigDecimal addPoboAmount(PoboTransactionReport poboTransactionReport, BigDecimal amountBefore, HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap) {
        BigDecimal rateToSGD = getRateToSGD(poboTransactionReport.recipientCurrency, poboTransactionReport.approvedTime, ratesMap);
        BigDecimal amountChanged = poboTransactionReport.recipientAmount.multiply(rateToSGD);
        return amountBefore.add(amountChanged);
    }

    private BigDecimal addFiatTransferAmount(FiatTransactionReport fiatTransactionReport, BigDecimal amountBefore, HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap) {
        BigDecimal rateToSGD = getRateToSGD(fiatTransactionReport.currency, fiatTransactionReport.completedTime, ratesMap);
        BigDecimal amountChanged = fiatTransactionReport.amount.multiply(rateToSGD);
        return amountBefore.add(amountChanged);
    }

    public static MasReportXlsxProcessor generate1a(
            LocalDate startDate,
            LocalDate endDate,
            List<MonitoringMatrix> monitoringMatrixList,
            List<WalletBalanceHistory> walletBalanceHistoryList,
            HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap
    ) throws IOException, IllegalAccessException {
        // Initial report processor
        MasReportXlsxProcessor processor = initReportWorkbook("1A");
        // Initial Summary Sheet with Title
        XSSFSheet sheet0 = initSummarySheet(processor, startDate, endDate, "1A");
        // Form 1A-1 (Don't have eMoney for now)
        processor.getCellByPos(sheet0, "B6").setCellValue("0.00"); // 1-a
        processor.getCellByPos(sheet0, "C6").setCellValue(0);
        processor.getCellByPos(sheet0, "B7").setCellValue("0.00"); // 1-b
        processor.getCellByPos(sheet0, "C7").setCellValue(0);
        // Form 1A-2
        int countOfPlacement = 0;
        BigDecimal valueOfPlacement = ZERO;
        int countOfWithdrawal = 0;
        BigDecimal valueOfWithdrawal = ZERO;
        for (WalletBalanceHistory walletBalanceHistory : walletBalanceHistoryList) {
            BigDecimal rateToSGD = getRateToSGD(walletBalanceHistory.currency, walletBalanceHistory.lastUpdatedDate, ratesMap);
            if (walletBalanceHistory.changeAmount.compareTo(ZERO) > 0) {
                countOfPlacement++;
                valueOfPlacement = valueOfPlacement.add(walletBalanceHistory.changeAmount.multiply(rateToSGD));
            } else {
                countOfWithdrawal++;
                valueOfWithdrawal = valueOfWithdrawal.add(walletBalanceHistory.changeAmount.multiply(rateToSGD));
            }
        }
        processor.getCellByPos(sheet0, "B10").setCellValue(valueOfPlacement.toString()); // 2-a
        processor.getCellByPos(sheet0, "C10").setCellValue(countOfPlacement);
        processor.getCellByPos(sheet0, "B11").setCellValue(valueOfWithdrawal.toString()); // 2-b
        processor.getCellByPos(sheet0, "C11").setCellValue(countOfWithdrawal);
        // Form 1A-3
        int countDomestic = 0;
        int countCrossBorder = 0;
        int countMerchantAcquisition = 0;
        int countEmoney = 0;
        int countDpt = 0;
        for (MonitoringMatrix monitoringMatrix : monitoringMatrixList) {
            // Domestic Transfer and Cross-border Transfer account is default
            if (monitoringMatrix.fiatAccountEnabled) {
                countDomestic++;
                countCrossBorder++;
            }
            if (monitoringMatrix.dptEnabled) {
                countDpt = countDpt + 1;
            }
            if (monitoringMatrix.emoneyEnabled) {
                countEmoney++;
            }
            if (monitoringMatrix.paymentEnabled) {
                countMerchantAcquisition++;
            }
        }
        processor.getCellByPos(sheet0, "B14").setCellValue(countDomestic); // 3-a
        processor.getCellByPos(sheet0, "B15").setCellValue(countCrossBorder); // 3-b
        processor.getCellByPos(sheet0, "B16").setCellValue(countMerchantAcquisition); // 3-c
        processor.getCellByPos(sheet0, "B17").setCellValue(countEmoney); // 3-d
        processor.getCellByPos(sheet0, "B18").setCellValue(countDpt); // 3-e
        processor.getCellByPos(sheet0, "B19").setCellValue(0); // 3-f
        // Form 1A-4
        processor.getCellByPos(sheet0, "B22").setCellValue(monitoringMatrixList.size()); // 4
        /*
                SHEET 1 Account Issued
         */
        generateAccountSheet(processor, monitoringMatrixList);
        return processor;
    }

    public static MasReportXlsxProcessor generate1b(
            LocalDate startDate,
            LocalDate endDate,
            List<RiskMatrix> riskMatrixList
    ) throws IOException, IllegalAccessException {
        // Initial report processor
        MasReportXlsxProcessor processor = initReportWorkbook("1B");
        // Initial Summary Sheet with Title
        XSSFSheet sheet0 = initSummarySheet(processor, startDate, endDate, "1B");
        processor.getCellByPos(sheet0, "B6").setCellValue(riskMatrixList.size());
        /*
                SHEET 1 Risk Matrix
         */
        generateRiskMatrixSheet(processor, riskMatrixList);
        return processor;
    }

    public static MasReportXlsxProcessor generate2a(
            LocalDate startDate,
            LocalDate endDate,
            List<FiatTransactionReport> fiatTransactionList,
            List<PoboTransactionReport> poboTransactionList,
            HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap
    ) throws IOException, IllegalAccessException {
        log.debug("generate2a \n {} \n {} \n {}", fiatTransactionList, poboTransactionList, ratesMap);
        // Initial report processor
        MasReportXlsxProcessor processor = initReportWorkbook("2A");
        // Initial Summary Sheet with Title
        XSSFSheet sheet0 = initSummarySheet(processor, startDate, endDate, "2A");
        // Form 2A-1
        BigDecimal fiatAmount = fiatTransactionList.stream()
                .map(fiatTransaction -> fiatTransaction.amount.multiply(getRateToSGD(fiatTransaction.currency, fiatTransaction.completedTime, ratesMap)))
                .reduce(ZERO, BigDecimal::add);
        BigDecimal poboAmount = poboTransactionList.stream()
                .map(poboTransaction -> poboTransaction.recipientAmount.multiply(getRateToSGD(poboTransaction.recipientCurrency, poboTransaction.approvedTime, ratesMap)))
                .reduce(ZERO, BigDecimal::add);
        processor.getCellByPos(sheet0, "B6").setCellValue(fiatAmount.add(poboAmount).toString());
        processor.getCellByPos(sheet0, "C6").setCellValue(fiatTransactionList.size() + poboTransactionList.size());
        /*
                SHEET 1 Domestic Transfer Transaction
         */
        generateFiatTransactionSheet(processor, fiatTransactionList, ratesMap);
        /*
                SHEET 2 Domestic Pobo Transaction
         */
        generatePoboTransactionSheet(processor, poboTransactionList, ratesMap);
        return processor;
    }

    public static MasReportXlsxProcessor generate2b(
            LocalDate startDate,
            LocalDate endDate,
            List<FiatTransactionReport> fiatTransactionList,
            List<PoboTransactionReport> poboTransactionList,
            Set<Long> clientInSGP,
            List<RiskMatrix> riskMatrixList,
            HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap
    ) throws IOException, IllegalAccessException {
        // Initial report processor
        MasReportXlsxProcessor processor = initReportWorkbook("2B");
        // Initial Summary Sheet with Title
        XSSFSheet sheet0 = initSummarySheet(processor, startDate, endDate, "2B");

        BigDecimal totalAmountIndividualInSGP = ZERO;
        int countIndividualInSGP = 0;
        BigDecimal totalAmountNonIndividualInSGP = ZERO;
        int countNonIndividualInSGP = 0;
        BigDecimal totalAmountIndividualOutSGP = ZERO;
        int countIndividualOutSGP = 0;
        BigDecimal totalAmountNonIndividualOutSGP = ZERO;
        int countNonIndividualOutSGP = 0;
        BigDecimal totalAmountHighRisk = ZERO;
        int countHighRisk = 0;
        Set<Long> highRiskClient = riskMatrixList.stream()
                .map(RiskMatrix::getClientId)
                .collect(Collectors.toSet());
        // Calculate POBO Transaction
        for (PoboTransaction poboTransaction : poboTransactionList) {
            if (ClientTypeUtils.isIndividual(poboTransaction.clientId)) {
                if (clientInSGP.contains(poboTransaction.clientId)) {
                    countIndividualInSGP++;
                    totalAmountIndividualInSGP = totalAmountIndividualInSGP.add(
                            poboTransaction.originatorAmount.multiply(getRateToSGD(poboTransaction.senderCurrency, poboTransaction.approvedTime, ratesMap)));
                } else {
                    countIndividualOutSGP++;
                    totalAmountIndividualOutSGP = totalAmountIndividualOutSGP.add(
                            poboTransaction.originatorAmount.multiply(getRateToSGD(poboTransaction.senderCurrency, poboTransaction.approvedTime, ratesMap)));
                }
            } else {
                if (clientInSGP.contains(poboTransaction.clientId)) {
                    countNonIndividualInSGP++;
                    totalAmountNonIndividualInSGP = totalAmountNonIndividualInSGP.add(
                            poboTransaction.originatorAmount.multiply(getRateToSGD(poboTransaction.senderCurrency, poboTransaction.approvedTime, ratesMap)));
                } else {
                    countNonIndividualOutSGP++;
                    totalAmountNonIndividualOutSGP = totalAmountNonIndividualOutSGP.add(
                            poboTransaction.originatorAmount.multiply(getRateToSGD(poboTransaction.senderCurrency, poboTransaction.approvedTime, ratesMap)));
                }
            }
            if (highRiskClient.contains(poboTransaction.clientId)) {
                countHighRisk++;
                totalAmountHighRisk = totalAmountHighRisk.add(
                        poboTransaction.originatorAmount.multiply(getRateToSGD(poboTransaction.senderCurrency, poboTransaction.approvedTime, ratesMap)));
            }
        }
        // Calculate Fiat Transaction
        for (FiatTransaction fiatTransaction : fiatTransactionList) {
            if (ClientTypeUtils.isIndividual(fiatTransaction.clientId)) {
                if (clientInSGP.contains(fiatTransaction.clientId)) {
                    countIndividualInSGP++;
                    totalAmountIndividualInSGP = totalAmountIndividualInSGP.add(
                            fiatTransaction.amount.multiply(getRateToSGD(fiatTransaction.currency, fiatTransaction.completedTime, ratesMap)));
                } else {
                    countIndividualOutSGP++;
                    totalAmountIndividualOutSGP = totalAmountIndividualOutSGP.add(
                            fiatTransaction.amount.multiply(getRateToSGD(fiatTransaction.currency, fiatTransaction.completedTime, ratesMap)));
                }
            } else {
                if (clientInSGP.contains(fiatTransaction.clientId)) {
                    countNonIndividualInSGP++;
                    totalAmountNonIndividualInSGP = totalAmountNonIndividualInSGP.add(
                            fiatTransaction.amount.multiply(getRateToSGD(fiatTransaction.currency, fiatTransaction.completedTime, ratesMap)));
                } else {
                    countNonIndividualOutSGP++;
                    totalAmountNonIndividualOutSGP = totalAmountNonIndividualOutSGP.add(
                            fiatTransaction.amount.multiply(getRateToSGD(fiatTransaction.currency, fiatTransaction.completedTime, ratesMap)));
                }
            }
            if (highRiskClient.contains(fiatTransaction.clientId)) {
                countHighRisk++;
                totalAmountHighRisk = totalAmountHighRisk.add(
                        fiatTransaction.amount.multiply(getRateToSGD(fiatTransaction.currency, fiatTransaction.completedTime, ratesMap)));
            }
        }
        // Form 2B-1
        processor.getCellByPos(sheet0, "B7").setCellValue(totalAmountIndividualInSGP.toString());
        processor.getCellByPos(sheet0, "C7").setCellValue(countIndividualInSGP);
        processor.getCellByPos(sheet0, "B8").setCellValue(totalAmountNonIndividualInSGP.toString());
        processor.getCellByPos(sheet0, "C8").setCellValue(countNonIndividualInSGP);
        // Form 2B-2
        processor.getCellByPos(sheet0, "B10").setCellValue(totalAmountIndividualOutSGP.toString());
        processor.getCellByPos(sheet0, "C10").setCellValue(countIndividualOutSGP);
        processor.getCellByPos(sheet0, "B11").setCellValue(totalAmountNonIndividualOutSGP.toString());
        processor.getCellByPos(sheet0, "C11").setCellValue(countNonIndividualOutSGP);
        // Form 2B-3
        processor.getCellByPos(sheet0, "B14").setCellValue(totalAmountHighRisk.toString());
        processor.getCellByPos(sheet0, "C14").setCellValue(countHighRisk);
        /*
                SHEET 1 Domestic Transfer Transaction
         */
        generateFiatTransactionSheet(processor, fiatTransactionList, ratesMap);
        /*
                SHEET 2 Domestic Pobo Transaction
         */
        generatePoboTransactionSheet(processor, poboTransactionList, ratesMap);
        /*
                SHEET 3 Risk Matrix
         */
        generateRiskMatrixSheet(processor, riskMatrixList);
        return processor;
    }

    public static MasReportXlsxProcessor generate3a(
            LocalDate startDate,
            LocalDate endDate,
            List<FiatTransactionReport> fiatTransactionList,
            List<PoboTransactionReport> poboTransactionList,
            HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap
    ) throws IOException, IllegalAccessException {
        // Initial report processor
        MasReportXlsxProcessor processor = initReportWorkbook("3A");
        // Initial Summary Sheet with Title
        XSSFSheet sheet0 = initSummarySheet(processor, startDate, endDate, "3A");

        int countOutward = 0;
        BigDecimal outwardTotalAmount = ZERO;
        int countInward = 0;
        BigDecimal inwardTotalAmount = ZERO;
        for (FiatTransaction fiatTransaction : fiatTransactionList) {
            if (fiatTransaction.type == FiatTransactionType.WITHDRAW) {
                countOutward++;
                outwardTotalAmount = outwardTotalAmount.add(
                        fiatTransaction.amount.multiply(getRateToSGD(fiatTransaction.currency, fiatTransaction.completedTime, ratesMap)));
            } else {
                countInward++;
                inwardTotalAmount = inwardTotalAmount.add(
                        fiatTransaction.amount.multiply(getRateToSGD(fiatTransaction.currency, fiatTransaction.completedTime, ratesMap)));
            }
        }
        //  POBO doesn't have inward transaction
        for (PoboTransaction poboTransaction : poboTransactionList) {
            countOutward++;
            outwardTotalAmount = outwardTotalAmount.add(
                    poboTransaction.originatorAmount.multiply(getRateToSGD(poboTransaction.senderCurrency, poboTransaction.approvedTime, ratesMap)));
        }

        // Form 3A-1 Outward Cross-border Transaction
        processor.getCellByPos(sheet0, "B6").setCellValue(outwardTotalAmount.toString());
        processor.getCellByPos(sheet0, "C6").setCellValue(countOutward);
        // Form 3A-2 Inward Cross-border Transaction
        processor.getCellByPos(sheet0, "B9").setCellValue(inwardTotalAmount.toString());
        processor.getCellByPos(sheet0, "C9").setCellValue(countInward);
        /*
                SHEET 1 Cross-border Transfer Transaction
         */
        generateFiatTransactionSheet(processor, fiatTransactionList, ratesMap);
        /*
                SHEET 2 Cross-border Pobo Transaction
         */
        generatePoboTransactionSheet(processor, poboTransactionList, ratesMap);
        return processor;
    }

    public static MasReportXlsxProcessor generate3b(
            LocalDate startDate,
            LocalDate endDate,
            List<FiatTransactionReport> fiatTransactionList,
            List<PoboTransactionReport> poboTransactionList,
            Set<Long> clientInSGP,
            Set<Long> fiClient,
            List<RiskMatrix> riskMatrixList,
            HashMap<LocalDate, HashMap<Currency, BigDecimal>> ratesMap
    ) throws IOException, IllegalAccessException {
        // Initial report processor
        MasReportXlsxProcessor processor = initReportWorkbook("3B");
        // Initial Summary Sheet with Title
        XSSFSheet sheet0 = initSummarySheet(processor, startDate, endDate, "3B");

        BigDecimal totalOutwardAmountFiInSGP = ZERO;
        int countOutwardFiInSGP = 0;
        BigDecimal totalOutwardAmountIndividualInSGP = ZERO;
        int countOutwardIndividualInSGP = 0;
        BigDecimal totalOutwardAmountNonIndividualInSGP = ZERO;
        int countOutwardNonIndividualInSGP = 0;

        BigDecimal totalOutwardAmountFiOutSGP = ZERO;
        int countOutwardFiOutSGP = 0;
        BigDecimal totalOutwardAmountIndividualOutSGP = ZERO;
        int countOutwardIndividualOutSGP = 0;
        BigDecimal totalOutwardAmountNonIndividualOutSGP = ZERO;
        int countOutwardNonIndividualOutSGP = 0;

        BigDecimal totalOutwardAmountToBank = ZERO;
        int countOutwardToBank = 0;

        BigDecimal totalInwardAmountFiInSGP = ZERO;
        int countInwardFiInSGP = 0;
        BigDecimal totalInwardAmountIndividualInSGP = ZERO;
        int countInwardIndividualInSGP = 0;
        BigDecimal totalInwardAmountNonIndividualInSGP = ZERO;
        int countInwardNonIndividualInSGP = 0;

        BigDecimal totalInwardAmountFiOutSGP = ZERO;
        int countInwardFiOutSGP = 0;
        BigDecimal totalInwardAmountIndividualOutSGP = ZERO;
        int countInwardIndividualOutSGP = 0;
        BigDecimal totalInwardAmountNonIndividualOutSGP = ZERO;
        int countInwardNonIndividualOutSGP = 0;

        BigDecimal totalInwardAmountToBank = ZERO;
        int countInwardToBank = 0;

        BigDecimal totalAmountHighRisk = ZERO;
        int countHighRisk = 0;

        Set<Long> highRiskClient = riskMatrixList.stream()
                .map(RiskMatrix::getClientId)
                .collect(Collectors.toSet());

        // POBO transactions are all outward
        for (PoboTransaction poboTransaction : poboTransactionList) {
            if (ClientTypeUtils.isIndividual(poboTransaction.clientId)) {
                // Individual client will not contains FI
                if (clientInSGP.contains(poboTransaction.clientId)) {
                    countOutwardIndividualInSGP++;
                    totalOutwardAmountIndividualInSGP = totalOutwardAmountIndividualInSGP.add(
                            poboTransaction.originatorAmount.multiply(getRateToSGD(poboTransaction.senderCurrency, poboTransaction.approvedTime, ratesMap)));
                } else {
                    countOutwardIndividualOutSGP++;
                    totalOutwardAmountIndividualOutSGP = totalOutwardAmountIndividualOutSGP.add(
                            poboTransaction.originatorAmount.multiply(getRateToSGD(poboTransaction.senderCurrency, poboTransaction.approvedTime, ratesMap)));
                }
            } else {
                // Non-individual includes FI
                if (clientInSGP.contains(poboTransaction.clientId)) {
                    if (fiClient.contains(poboTransaction.clientId)) {
                        countOutwardFiInSGP++;
                        totalOutwardAmountFiInSGP = totalOutwardAmountFiInSGP.add(
                                poboTransaction.originatorAmount.multiply(getRateToSGD(poboTransaction.senderCurrency, poboTransaction.approvedTime, ratesMap)));
                    } else {
                        countOutwardNonIndividualInSGP++;
                        totalOutwardAmountNonIndividualInSGP = totalOutwardAmountNonIndividualInSGP.add(
                                poboTransaction.originatorAmount.multiply(getRateToSGD(poboTransaction.senderCurrency, poboTransaction.approvedTime, ratesMap)));
                    }
                } else {
                    if (fiClient.contains(poboTransaction.clientId)) {
                        countOutwardFiOutSGP++;
                        totalOutwardAmountFiOutSGP = totalOutwardAmountFiOutSGP.add(
                                poboTransaction.originatorAmount.multiply(getRateToSGD(poboTransaction.senderCurrency, poboTransaction.approvedTime, ratesMap)));
                    } else {
                        countOutwardNonIndividualOutSGP++;
                        totalOutwardAmountNonIndividualOutSGP = totalOutwardAmountNonIndividualOutSGP.add(
                                poboTransaction.originatorAmount.multiply(getRateToSGD(poboTransaction.senderCurrency, poboTransaction.approvedTime, ratesMap)));
                    }
                }
            }
            if (highRiskClient.contains(poboTransaction.clientId)) {
                countHighRisk++;
                totalAmountHighRisk = totalAmountHighRisk.add(
                        poboTransaction.originatorAmount.multiply(getRateToSGD(poboTransaction.senderCurrency, poboTransaction.approvedTime, ratesMap)));
            }
            countOutwardToBank++;
            totalOutwardAmountToBank = totalOutwardAmountToBank.add(
                    poboTransaction.originatorAmount.multiply(getRateToSGD(poboTransaction.senderCurrency, poboTransaction.approvedTime, ratesMap)));
        }

        // fiatTransaction.type DEPOSIT for inward, fiatTransaction.type WITHDRAWAL for outward
        for (FiatTransaction fiatTransaction : fiatTransactionList) {
            if (fiatTransaction.type == FiatTransactionType.DEPOSIT) {
                if (ClientTypeUtils.isIndividual(fiatTransaction.clientId)) {
                    // Individual client will not contains FI
                    if (clientInSGP.contains(fiatTransaction.clientId)) {
                        countInwardIndividualInSGP++;
                        totalInwardAmountIndividualInSGP = totalInwardAmountIndividualInSGP.add(
                                fiatTransaction.amount.multiply(getRateToSGD(fiatTransaction.currency, fiatTransaction.completedTime, ratesMap)));
                    } else {
                        countInwardIndividualOutSGP++;
                        totalInwardAmountIndividualOutSGP = totalInwardAmountIndividualOutSGP.add(
                                fiatTransaction.amount.multiply(getRateToSGD(fiatTransaction.currency, fiatTransaction.completedTime, ratesMap)));
                    }
                } else {
                    // Non-individual includes FI
                    if (clientInSGP.contains(fiatTransaction.clientId)) {
                        if (fiClient.contains(fiatTransaction.clientId)) {
                            countInwardFiInSGP++;
                            totalInwardAmountFiInSGP = totalInwardAmountFiInSGP.add(
                                    fiatTransaction.amount.multiply(getRateToSGD(fiatTransaction.currency, fiatTransaction.completedTime, ratesMap)));
                        } else {
                            countInwardNonIndividualInSGP++;
                            totalInwardAmountNonIndividualInSGP = totalInwardAmountNonIndividualInSGP.add(
                                    fiatTransaction.amount.multiply(getRateToSGD(fiatTransaction.currency, fiatTransaction.completedTime, ratesMap)));
                        }
                    } else {
                        if (fiClient.contains(fiatTransaction.clientId)) {
                            countInwardFiOutSGP++;
                            totalInwardAmountFiOutSGP = totalInwardAmountFiOutSGP.add(
                                    fiatTransaction.amount.multiply(getRateToSGD(fiatTransaction.currency, fiatTransaction.completedTime, ratesMap)));
                        } else {
                            countInwardNonIndividualOutSGP++;
                            totalInwardAmountNonIndividualOutSGP = totalInwardAmountNonIndividualOutSGP.add(
                                    fiatTransaction.amount.multiply(getRateToSGD(fiatTransaction.currency, fiatTransaction.completedTime, ratesMap)));
                        }
                    }
                }
                if (highRiskClient.contains(fiatTransaction.clientId)) {
                    countHighRisk++;
                    totalAmountHighRisk = totalAmountHighRisk.add(
                            fiatTransaction.amount.multiply(getRateToSGD(fiatTransaction.currency, fiatTransaction.completedTime, ratesMap)));
                }
                countInwardToBank++;
                totalInwardAmountToBank = totalOutwardAmountToBank.add(
                        fiatTransaction.amount.multiply(getRateToSGD(fiatTransaction.currency, fiatTransaction.completedTime, ratesMap)));
            } else {
                if (ClientTypeUtils.isIndividual(fiatTransaction.clientId)) {
                    // Individual client will not contains FI
                    if (clientInSGP.contains(fiatTransaction.clientId)) {
                        countOutwardIndividualInSGP++;
                        totalOutwardAmountIndividualInSGP = totalOutwardAmountIndividualInSGP.add(
                                fiatTransaction.amount.multiply(getRateToSGD(fiatTransaction.currency, fiatTransaction.completedTime, ratesMap)));
                    } else {
                        countOutwardIndividualOutSGP++;
                        totalOutwardAmountIndividualOutSGP = totalOutwardAmountIndividualOutSGP.add(
                                fiatTransaction.amount.multiply(getRateToSGD(fiatTransaction.currency, fiatTransaction.completedTime, ratesMap)));
                    }
                } else {
                    // Non-individual includes FI
                    if (clientInSGP.contains(fiatTransaction.clientId)) {
                        if (fiClient.contains(fiatTransaction.clientId)) {
                            countOutwardFiInSGP++;
                            totalOutwardAmountFiInSGP = totalOutwardAmountFiInSGP.add(
                                    fiatTransaction.amount.multiply(getRateToSGD(fiatTransaction.currency, fiatTransaction.completedTime, ratesMap)));
                        } else {
                            countOutwardNonIndividualInSGP++;
                            totalOutwardAmountNonIndividualInSGP = totalOutwardAmountNonIndividualInSGP.add(
                                    fiatTransaction.amount.multiply(getRateToSGD(fiatTransaction.currency, fiatTransaction.completedTime, ratesMap)));
                        }
                    } else {
                        if (fiClient.contains(fiatTransaction.clientId)) {
                            countOutwardFiOutSGP++;
                            totalOutwardAmountFiOutSGP = totalOutwardAmountFiOutSGP.add(
                                    fiatTransaction.amount.multiply(getRateToSGD(fiatTransaction.currency, fiatTransaction.completedTime, ratesMap)));
                        } else {
                            countOutwardNonIndividualOutSGP++;
                            totalOutwardAmountNonIndividualOutSGP = totalOutwardAmountNonIndividualOutSGP.add(
                                    fiatTransaction.amount.multiply(getRateToSGD(fiatTransaction.currency, fiatTransaction.completedTime, ratesMap)));
                        }
                    }
                }
                if (highRiskClient.contains(fiatTransaction.clientId)) {
                    countHighRisk++;
                    totalAmountHighRisk = totalAmountHighRisk.add(
                            fiatTransaction.amount.multiply(getRateToSGD(fiatTransaction.currency, fiatTransaction.completedTime, ratesMap)));
                }
                countOutwardToBank++;
                totalOutwardAmountToBank = totalOutwardAmountToBank.add(
                        fiatTransaction.amount.multiply(getRateToSGD(fiatTransaction.currency, fiatTransaction.completedTime, ratesMap)));
            }
        }

        // All POBO transactions are outward
        HashMap<String, TotalSortingObject> outwardCountByCountry = poboTransactionList.stream()
                .collect(Collectors.toMap(
                        o -> o.recipientCountry,
                        x -> new TotalSortingObject(null, x.recipientCountry, null),
                        (left, right) -> {
                            left.totalCount++;
                            return left;
                        },
                        HashMap::new
                ));

        // Only fiat withdrawal transactions are outward
        HashMap<String, TotalSortingObject> outwardFiatCountByCountry = fiatTransactionList.stream()
                .filter(fiatTransactionReport -> fiatTransactionReport.type == FiatTransactionType.WITHDRAW)
                .collect(Collectors.toMap(
                        o -> o.recipientCountry,
                        x -> new TotalSortingObject(null, x.recipientCountry, null),
                        (left, right) -> {
                            left.totalCount++;
                            return left;
                        },
                        HashMap::new
                ));
        outwardCountByCountry.forEach(
                (k, v) -> outwardFiatCountByCountry.merge(k, v, (totalSortingObject, totalSortingObject2) -> {
                    totalSortingObject.totalCount = totalSortingObject.totalCount + totalSortingObject2.totalCount;
                    return totalSortingObject;
                })
        );

        List<TotalSortingObject> totalOutwardCountByCountryList =
                outwardCountByCountry
                        .values()
                        .stream()
                        .sorted(Collections.reverseOrder(Comparator.comparing(TotalSortingObject::getTotalCount)))
                        .limit(10)
                        .collect(Collectors.toList());

        // Only fiat deposit is inward
        HashMap<String, TotalSortingObject> inwardFiatCountByCountry = fiatTransactionList.stream()
                .filter(fiatTransactionReport -> fiatTransactionReport.type == FiatTransactionType.DEPOSIT)
                .collect(Collectors.toMap(
                        o -> o.recipientCountry,
                        x -> new TotalSortingObject(null, x.recipientCountry, null),
                        (left, right) -> {
                            left.totalCount++;
                            return left;
                        },
                        HashMap::new
                ));

        List<TotalSortingObject> totalInwardCountByCountryList =
                inwardFiatCountByCountry
                        .values()
                        .stream()
                        .sorted(Collections.reverseOrder(Comparator.comparing(TotalSortingObject::getTotalCount)))
                        .limit(10)
                        .collect(Collectors.toList());

        // Form 3B-1 Outward in Singapore
        processor.getCellByPos(sheet0, "B7").setCellValue(totalOutwardAmountFiInSGP.toString()); // 3B-1 (a)
        processor.getCellByPos(sheet0, "E7").setCellValue(countOutwardFiInSGP);
        processor.getCellByPos(sheet0, "B9").setCellValue(totalOutwardAmountIndividualInSGP.toString()); // 3B-1 (b) (i)
        processor.getCellByPos(sheet0, "E9").setCellValue(countOutwardIndividualInSGP);
        processor.getCellByPos(sheet0, "B10").setCellValue(totalOutwardAmountNonIndividualInSGP.toString()); // 3B-1 (b) (ii)
        processor.getCellByPos(sheet0, "E10").setCellValue(countOutwardNonIndividualInSGP);
        // Form 3B-2 Outward outside Singapore
        processor.getCellByPos(sheet0, "B12").setCellValue(totalOutwardAmountFiOutSGP.toString()); // 3B-2 (a)
        processor.getCellByPos(sheet0, "E12").setCellValue(countOutwardFiOutSGP);
        processor.getCellByPos(sheet0, "B14").setCellValue(totalOutwardAmountIndividualOutSGP.toString()); // 3B-2 (b) (i)
        processor.getCellByPos(sheet0, "E14").setCellValue(countOutwardIndividualOutSGP);
        processor.getCellByPos(sheet0, "B15").setCellValue(totalOutwardAmountNonIndividualOutSGP.toString()); // 3B-2 (b) (ii)
        processor.getCellByPos(sheet0, "E15").setCellValue(countOutwardNonIndividualOutSGP);
        // Form 3B-3 Inward in Singapore
        processor.getCellByPos(sheet0, "B19").setCellValue(totalInwardAmountFiInSGP.toString()); // 3B-3 (a)
        processor.getCellByPos(sheet0, "E19").setCellValue(countInwardFiInSGP);
        processor.getCellByPos(sheet0, "B21").setCellValue(totalInwardAmountIndividualInSGP.toString()); // 3B-3 (b) (i)
        processor.getCellByPos(sheet0, "E21").setCellValue(countInwardIndividualInSGP);
        processor.getCellByPos(sheet0, "B22").setCellValue(totalInwardAmountNonIndividualInSGP.toString()); // 3B-3 (b) (ii)
        processor.getCellByPos(sheet0, "E22").setCellValue(countInwardNonIndividualInSGP);
        // Form 3B-4 Inward outside Singapore
        processor.getCellByPos(sheet0, "B24").setCellValue(totalInwardAmountFiOutSGP.toString()); // 3B-4 (a)
        processor.getCellByPos(sheet0, "E24").setCellValue(countInwardFiOutSGP);
        processor.getCellByPos(sheet0, "B26").setCellValue(totalInwardAmountIndividualOutSGP.toString()); // 3B-4 (b) (i)
        processor.getCellByPos(sheet0, "E26").setCellValue(countInwardIndividualOutSGP);
        processor.getCellByPos(sheet0, "B27").setCellValue(totalInwardAmountNonIndividualOutSGP.toString()); // 3B-4 (b) (ii)
        processor.getCellByPos(sheet0, "E27").setCellValue(countInwardNonIndividualOutSGP);
        // Form 3B-5 All outward funds are transferred to a bank
        processor.getCellByPos(sheet0, "B30").setCellValue(totalOutwardAmountToBank.toString()); // 3B-5 (a)
        processor.getCellByPos(sheet0, "D30").setCellValue(countOutwardToBank);
        processor.getCellByPos(sheet0, "B31").setCellValue("0.00"); // 3B-5 (b)
        processor.getCellByPos(sheet0, "D31").setCellValue(0);
        processor.getCellByPos(sheet0, "B32").setCellValue("0.00"); // 3B-5 (c)
        processor.getCellByPos(sheet0, "D32").setCellValue(0);
        processor.getCellByPos(sheet0, "B33").setCellValue("0.00"); // 3B-5 (d)
        processor.getCellByPos(sheet0, "D33").setCellValue(0);
        processor.getCellByPos(sheet0, "E33").setCellValue("NA");
        // Form 3B-6 All inward funds are transferred from a bank
        processor.getCellByPos(sheet0, "B36").setCellValue(totalInwardAmountToBank.toString()); // 3B-6 (a)
        processor.getCellByPos(sheet0, "D36").setCellValue(countInwardToBank);
        processor.getCellByPos(sheet0, "B37").setCellValue("0.00"); // 3B-6 (b)
        processor.getCellByPos(sheet0, "D37").setCellValue(0);
        processor.getCellByPos(sheet0, "B38").setCellValue("0.00"); // 3B-6 (c)
        processor.getCellByPos(sheet0, "D38").setCellValue(0);
        processor.getCellByPos(sheet0, "B39").setCellValue("0.00"); // 3B-6 (d)
        processor.getCellByPos(sheet0, "D39").setCellValue(0);
        processor.getCellByPos(sheet0, "E39").setCellValue("NA");
        // Form 3B-7 (a) Outward counts by country
        for (int i = 0; i < totalOutwardCountByCountryList.size(); i++) {
            int row = 42 + i;
            processor.getCellByPos(sheet0, "C" + row).setCellValue(totalOutwardCountByCountryList.get(i).country);
            processor.getCellByPos(sheet0, "D" + row).setCellValue(totalOutwardCountByCountryList.get(i).totalCount);
        }
        // Form 3B-7 (b) Inward counts by country
        for (int i = 0; i < totalInwardCountByCountryList.size(); i++) {
            int row = 53 + i;
            processor.getCellByPos(sheet0, "C" + row).setCellValue(totalInwardCountByCountryList.get(i).country);
            processor.getCellByPos(sheet0, "D" + row).setCellValue(totalInwardCountByCountryList.get(i).totalCount);
        }

        // Form 3B-8
        processor.getCellByPos(sheet0, "B65").setCellValue(totalAmountHighRisk.toString()); // 3B-6 (a)
        processor.getCellByPos(sheet0, "D65").setCellValue(countHighRisk);
        // Form 3B-9 All inward funds are transferred to DTC bank account
        processor.getCellByPos(sheet0, "B68").setCellValue(totalInwardAmountToBank.toString()); // 3B-9 (a)
        processor.getCellByPos(sheet0, "D68").setCellValue(countInwardToBank);
        processor.getCellByPos(sheet0, "B69").setCellValue("0.00"); // 3B-9 (b)
        processor.getCellByPos(sheet0, "D69").setCellValue(0);
        processor.getCellByPos(sheet0, "B70").setCellValue("0.00"); // 3B-9 (c)
        processor.getCellByPos(sheet0, "D70").setCellValue(0);
        processor.getCellByPos(sheet0, "E70").setCellValue("NA");
        // Form 3B-10 No agent used
        processor.getCellByPos(sheet0, "C73").setCellValue("NA");
        processor.getCellByPos(sheet0, "C84").setCellValue("NA");
        /*
                SHEET 1 Cross-border Transfer Transaction
         */
        generateFiatTransactionSheet(processor, fiatTransactionList, ratesMap);
        /*
                SHEET 3 Risk Matrix
         */
        generateRiskMatrixSheet(processor, riskMatrixList);
        return processor;
    }

//    public static MasReportXlsxProcessor generate4a(
//            LocalDate startDate,
//            LocalDate endDate,
//            List<PaymentTransaction> paymentTransactionList,
//            HashMap<Currency, BigDecimal> currentRateToSGD
//    ) throws IOException, IllegalAccessException {
//        MasReportXlsxProcessor processor = new MasReportXlsxProcessor();
//        processor.workbook = new XSSFWorkbook(MasReportXlsxProcessor.class.getResourceAsStream("/xlsx-templates/mas-report-4a.xlsx"));
//        processor.paymentTransactionList = paymentTransactionList;
//        processor.startDate = startDate;
//        processor.endDate = endDate;
//        processor.setCurrentRates(currentRateToSGD);
//        processor.fill4a();
//        return processor;
//    }
//
//    public static MasReportXlsxProcessor generate4b(
//            LocalDate startDate,
//            LocalDate endDate,
//            List<PaymentTransaction> paymentTransactionList,
//            List<NonIndividual> nonIndividualList,
//            List<Terminal> terminalList,
//            HashMap<Currency, BigDecimal> currentRateToSGD
//    ) throws IOException, IllegalAccessException {
//        MasReportXlsxProcessor processor = new MasReportXlsxProcessor();
//        processor.workbook = new XSSFWorkbook(MasReportXlsxProcessor.class.getResourceAsStream("/xlsx-templates/mas-report-4b.xlsx"));
//        processor.paymentTransactionList = paymentTransactionList;
//        processor.nonIndividualList = nonIndividualList;
//        processor.terminalList = terminalList;
//        processor.startDate = startDate;
//        processor.endDate = endDate;
//        processor.setCurrentRates(currentRateToSGD);
//        processor.fill4b();
//        return processor;
//    }
//
//    public static MasReportXlsxProcessor generate5(
//            LocalDate startDate,
//            LocalDate endDate
//    ) throws IOException, IllegalAccessException {
//        MasReportXlsxProcessor processor = new MasReportXlsxProcessor();
//        processor.workbook = new XSSFWorkbook(MasReportXlsxProcessor.class.getResourceAsStream("/xlsx-templates/mas-report-5.xlsx"));
//        processor.startDate = startDate;
//        processor.endDate = endDate;
//        processor.fill5();
//        return processor;
//    }
//
//    public static MasReportXlsxProcessor generate6a(
//            LocalDate startDate,
//            LocalDate endDate,
//            List<Otc> otcList,
//            HashMap<Currency, BigDecimal> currentRateToSGD
//    ) throws IOException, IllegalAccessException {
//        MasReportXlsxProcessor processor = new MasReportXlsxProcessor();
//        processor.workbook = new XSSFWorkbook(MasReportXlsxProcessor.class.getResourceAsStream("/xlsx-templates/mas-report-6a.xlsx"));
//        processor.otcList = otcList;
//        processor.startDate = startDate;
//        processor.endDate = endDate;
//        processor.setCurrentRates(currentRateToSGD);
//        processor.fill6a();
//        return processor;
//    }
//
//    public static MasReportXlsxProcessor generate6b(
//            LocalDate startDate,
//            LocalDate endDate,
//            List<Otc> otcList,
//            List<CryptoTransaction> cryptoTransactionList,
//            List<RiskMatrix> riskMatrixList,
//            List<Individual> individualList,
//            List<NonIndividual> nonIndividualList,
//            List<WalletAccount> cryptoAccountList,
//            Set<Long> highRiskCountryClientIds,
//            HashMap<Currency, BigDecimal> currentRateToSGD
//    ) throws IOException, IllegalAccessException {
//        MasReportXlsxProcessor processor = new MasReportXlsxProcessor();
//        processor.workbook = new XSSFWorkbook(MasReportXlsxProcessor.class.getResourceAsStream("/xlsx-templates/mas-report-6b.xlsx"));
//        processor.otcList = otcList;
//        processor.cryptoTransactionList = cryptoTransactionList;
//        processor.riskMatrixList = riskMatrixList;
//        processor.individualList = individualList;
//        processor.nonIndividualList = nonIndividualList;
//        processor.cryptoAccountList = cryptoAccountList;
//        processor.highRiskCountryClientIds = highRiskCountryClientIds;
//        processor.startDate = startDate;
//        processor.endDate = endDate;
//        processor.setCurrentRates(currentRateToSGD);
//        processor.fill6b();
//        return processor;
//    }
//
//    private void setCurrentRates(HashMap<Currency, BigDecimal> currentRateToSGD) {
//        log.info("Rates \n {}", currentRateToSGD);
//        this.rateFromUSD = currentRateToSGD.get(Currency.USD);
//        this.rateFromCNY = currentRateToSGD.get(Currency.CNY);
//        this.rateFromBTC = currentRateToSGD.get(Currency.BTC);
//        this.rateFromETH = currentRateToSGD.get(Currency.ETH);
//        this.rateFromTRX = currentRateToSGD.get(Currency.TRX);
//        this.rateFromUSDT = currentRateToSGD.get(Currency.USDT);
//        this.rateFromUSDC = currentRateToSGD.get(Currency.USDC);
//    }

    private void fill1a() throws IllegalAccessException {
    }

    private void fill1b() throws IllegalAccessException {
    }

    private void fill2a() throws IllegalAccessException {
    }

    private void fill2b() throws IllegalAccessException {
    }

    private void fill3a() throws IllegalAccessException {
    }

    private void fill3b() throws IllegalAccessException {
    }

//    private void fill4a() throws IllegalAccessException {
//        CellStyle percentCellStyle = this.workbook.createCellStyle();
//        percentCellStyle.setDataFormat(this.workbook.createDataFormat().getFormat("0%"));
//        /*
//                SHEET 0 Report 4A Summary
//         */
//        XSSFSheet sheet0 = this.workbook.getSheetAt(0);
//        XlsxGenerator.lock(sheet0);
//        this.workbook.setSheetName(0, "4A Summary");
//        // 4A Title
//        this.getCellByPos(sheet0, "B2").setCellValue("Digital Treasures Center Pte. Ltd.");
//        this.getCellByPos(sheet0, "B3").setCellValue(String.format("%s to %s",
//                startDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)), endDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))));
//        // Form 4A-1
//        BigDecimal totalAmountInSGP = ZERO;
//        int countInSGP = 0;
//        BigDecimal totalAmountOutSGP = ZERO;
//        int countOutSGP = 0;
//        for (PaymentTransaction paymentTransaction : paymentTransactionList) {
//            if ("SGP".equals(paymentTransaction.country)) {
//                countInSGP++;
//                totalAmountInSGP = this.addPaymentAmount(paymentTransaction, totalAmountInSGP);
//            } else {
//                countOutSGP++;
//                totalAmountOutSGP = this.addPaymentAmount(paymentTransaction, totalAmountOutSGP);
//            }
//        }
//        this.getCellByPos(sheet0, "B6").setCellValue(totalAmountInSGP.toString());
//        this.getCellByPos(sheet0, "C6").setCellValue(countInSGP);
//        this.getCellByPos(sheet0, "B7").setCellValue(totalAmountOutSGP.toString());
//        this.getCellByPos(sheet0, "C7").setCellValue(countOutSGP);
//        /*
//                SHEET 1 Merchant Acquisition Transaction
//         */
//        XlsxGenerator
//                .records(workbook, paymentTransactionList, PaymentTransactionReport.class)
//                .valueHandler((key, record) -> {
//                    PaymentTransaction paymentTransaction = (PaymentTransaction) record;
//                    if ("rateToSGD".equals(key)) {
//                        return getRateToSGD(paymentTransaction.requestCurrency);
//                    }
//                    return FieldValue.empty();
//                })
//                .genSheet("MA Transactions");
//        /*
//                SHEET 2 Non-individual Client
//         */
//        XlsxGenerator
//                .records(workbook, nonIndividualList, NonIndividualReport.class)
//                .genSheet("Non-individual Client");
//    }
//
//    private void fill4b() throws IllegalAccessException {
//        CellStyle percentCellStyle = this.workbook.createCellStyle();
//        percentCellStyle.setDataFormat(this.workbook.createDataFormat().getFormat("0%"));
//        /*
//                SHEET 0 Report 4B Summary
//         */
//        XSSFSheet sheet0 = this.workbook.getSheetAt(0);
//        XlsxGenerator.lock(sheet0);
//        this.workbook.setSheetName(0, "4A Summary");
//        // 4B Title
//        this.getCellByPos(sheet0, "B2").setCellValue("Digital Treasures Center Pte. Ltd.");
//        this.getCellByPos(sheet0, "B3").setCellValue(String.format("%s to %s",
//                startDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)), endDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))));
//        // Form 4B-1
//        long countPOS = terminalList.stream().filter(terminal -> terminal.type == TerminalType.POS).count();
//        this.getCellByPos(sheet0, "B6").setCellValue(countPOS);
//        this.getCellByPos(sheet0, "B7").setCellValue(countPOS);
//        // Form 4B-2
//        long countNonIndividualInSGP = 0;
//        long countNonIndividualOutSGP = 0;
//        for (NonIndividual nonIndividual : nonIndividualList) {
//            if ("SGP".equals(nonIndividual.country)) {
//                countNonIndividualInSGP++;
//            } else {
//                countNonIndividualOutSGP++;
//            }
//        }
//        this.getCellByPos(sheet0, "B10").setCellValue(countNonIndividualInSGP);
//        this.getCellByPos(sheet0, "B11").setCellValue(countNonIndividualOutSGP);
//        // Form 4B-3
//        List<TotalCountByMerchant> totalCountByMerchantList =
//                paymentTransactionList.stream()
//                        .collect(Collectors.toMap(
//                                PaymentTransaction::getCountry,
//                                x -> {
//                                    TotalCountByMerchant totalCountByMerchant = new TotalCountByMerchant();
//                                    totalCountByMerchant.clientId = x.merchantId;
//                                    totalCountByMerchant.clientName = x.merchantName;
//                                    return totalCountByMerchant;
//                                },
//                                (left, right) -> {
//                                    left.totalCount++;
//                                    return left;
//                                },
//                                HashMap::new
//                        ))
//                        .values()
//                        .stream()
//                        .sorted(Collections.reverseOrder(Comparator.comparing(TotalCountByMerchant::getTotalCount)))
//                        .limit(10)
//                        .collect(Collectors.toList());
//        for (int i = 0; i < totalCountByMerchantList.size(); i++) {
//            int row = 14 + i;
//            this.getCellByPos(sheet0, "C" + row).setCellValue(totalCountByMerchantList.get(i).clientName);
//            this.getCellByPos(sheet0, "E" + row).setCellValue(totalCountByMerchantList.get(i).totalCount);
//        }
//        /*
//                SHEET 1 Merchant Acquisition Transaction
//         */
//        XlsxGenerator
//                .records(workbook, paymentTransactionList, PaymentTransactionReport.class)
//                .valueHandler((key, record) -> {
//                    PaymentTransaction paymentTransaction = (PaymentTransaction) record;
//                    if ("rateToSGD".equals(key)) {
//                        return getRateToSGD(paymentTransaction.requestCurrency);
//                    }
//                    return FieldValue.empty();
//                })
//                .genSheet("MA Transactions");
//        /*
//                SHEET 2 Non-individual Client
//         */
//        XlsxGenerator
//                .records(workbook, nonIndividualList, NonIndividualReport.class)
//                .genSheet("Non-individual Client");
//    }
//
//    private void fill5() throws IllegalAccessException {
//        CellStyle percentCellStyle = this.workbook.createCellStyle();
//        percentCellStyle.setDataFormat(this.workbook.createDataFormat().getFormat("0%"));
//        /*
//                SHEET 0 Report 5 Summary
//         */
//        XSSFSheet sheet0 = this.workbook.getSheetAt(0);
//        XlsxGenerator.lock(sheet0);
//        this.workbook.setSheetName(0, "5 Summary");
//        // 5 Title
//        this.getCellByPos(sheet0, "B2").setCellValue("Digital Treasures Center Pte. Ltd.");
//        this.getCellByPos(sheet0, "B3").setCellValue(String.format("%s to %s",
//                startDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)), endDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))));
//        // Form 5
//        this.getCellByPos(sheet0, "B6").setCellValue("0.00");
//    }
//
//    private void fill6a() throws IllegalAccessException {
//        CellStyle percentCellStyle = this.workbook.createCellStyle();
//        percentCellStyle.setDataFormat(this.workbook.createDataFormat().getFormat("0%"));
//        /*
//                SHEET 0 Report 6A Summary
//         */
//        XSSFSheet sheet0 = this.workbook.getSheetAt(0);
//        XlsxGenerator.lock(sheet0);
//        this.workbook.setSheetName(0, "6A Summary");
//        // 6A Title
//        this.getCellByPos(sheet0, "B2").setCellValue("Digital Treasures Center Pte. Ltd.");
//        this.getCellByPos(sheet0, "B3").setCellValue(String.format("%s to %s",
//                startDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)), endDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))));
//        // Form 6A-1
//        BigDecimal totalBuyTokenAmount = ZERO;
//        int countBuy = 0;
//        BigDecimal totalSellTokenAmount = ZERO;
//        int countSell = 0;
//        for (Otc otc : otcList) {
//            if (otc.type == OtcType.BUYING) {
//                countBuy++;
//                totalBuyTokenAmount = this.addOtcAmount(otc, totalBuyTokenAmount);
//            } else {
//                countSell++;
//                totalSellTokenAmount = this.addOtcAmount(otc, totalSellTokenAmount);
//            }
//        }
//        this.getCellByPos(sheet0, "B6").setCellValue(totalBuyTokenAmount.toString());
//        this.getCellByPos(sheet0, "C6").setCellValue(countBuy);
//        this.getCellByPos(sheet0, "B7").setCellValue(totalSellTokenAmount.toString());
//        this.getCellByPos(sheet0, "C7").setCellValue(countSell);
//        this.getCellByPos(sheet0, "B8").setCellValue("0.00");
//        this.getCellByPos(sheet0, "C8").setCellValue(0);
//        /*
//                SHEET 1 OTC Transaction
//         */
//        XlsxGenerator
//                .records(workbook, otcList, OtcReport.class)
//                .valueHandler((key, record) -> {
//                    Otc otc = (Otc) record;
//                    if ("rateToSGD".equals(key)) {
//                        return getRateToSGD(otc.fiatCurrency);
//                    }
//                    return FieldValue.empty();
//                })
//                .genSheet("OTC Transaction");
//    }
//
//    private void fill6b() throws IllegalAccessException {
//        CellStyle percentCellStyle = this.workbook.createCellStyle();
//        percentCellStyle.setDataFormat(this.workbook.createDataFormat().getFormat("0%"));
//        /*
//                SHEET 0 Report 6B Summary
//         */
//        XSSFSheet sheet0 = this.workbook.getSheetAt(0);
//        XlsxGenerator.lock(sheet0);
//        this.workbook.setSheetName(0, "3A Summary");
//        // 6B Title
//        this.getCellByPos(sheet0, "B2").setCellValue("Digital Treasures Center Pte. Ltd.");
//        this.getCellByPos(sheet0, "B3").setCellValue(String.format("%s to %s",
//                startDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)), endDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))));
//        // Form 6B-1 (a)
//        BigDecimal totalBuyTokenAmount = ZERO;
//        int countBuy = 0;
//        BigDecimal totalSellTokenAmount = ZERO;
//        int countSell = 0;
//        for (Otc otc : otcList) {
//            if (otc.type == OtcType.BUYING) {
//                countBuy++;
//                totalBuyTokenAmount = this.addOtcAmount(otc, totalBuyTokenAmount);
//            } else {
//                countSell++;
//                totalSellTokenAmount = this.addOtcAmount(otc, totalSellTokenAmount);
//            }
//        }
//        this.getCellByPos(sheet0, "B7").setCellValue(totalBuyTokenAmount.toString());
//        this.getCellByPos(sheet0, "E7").setCellValue(countBuy);
//        this.getCellByPos(sheet0, "B8").setCellValue(totalSellTokenAmount.toString());
//        this.getCellByPos(sheet0, "E8").setCellValue(countSell);
//        this.getCellByPos(sheet0, "B9").setCellValue("0.00");
//        this.getCellByPos(sheet0, "C9").setCellValue(0);
//        // Form 6B-1 (b) Not facilitating exchange of DPT on platform
//        this.getCellByPos(sheet0, "B11").setCellValue("0.00");
//        this.getCellByPos(sheet0, "E11").setCellValue(0);
//        this.getCellByPos(sheet0, "B12").setCellValue("0.00");
//        this.getCellByPos(sheet0, "E12").setCellValue(0);
//        this.getCellByPos(sheet0, "B13").setCellValue("0.00");
//        this.getCellByPos(sheet0, "E13").setCellValue(0);
//        // Form 6B-2 (a) Not allowed to transfer within platform
//        this.getCellByPos(sheet0, "B16").setCellValue("0.00");
//        this.getCellByPos(sheet0, "E16").setCellValue(0);
//        // Form 6B-2 (b) All crypto transfer out to cold wallet
//        BigDecimal totalWithdrawalAmount = cryptoTransactionList.stream()
//                .map(cryptoTransaction -> cryptoTransaction.amount.multiply(rateFromUSD)).reduce(BigDecimal.ZERO, BigDecimal::add);
//        this.getCellByPos(sheet0, "B18").setCellValue("0.00");
//        this.getCellByPos(sheet0, "E18").setCellValue(0);
//        this.getCellByPos(sheet0, "B19").setCellValue("0.00");
//        this.getCellByPos(sheet0, "E19").setCellValue(0);
//        this.getCellByPos(sheet0, "B20").setCellValue(totalWithdrawalAmount.setScale(2, RoundingMode.HALF_UP).toString());
//        this.getCellByPos(sheet0, "E20").setCellValue(cryptoTransactionList.size());
//        // Form 6B-3
//        long countNonIndividualInSGP = 0;
//        long countNonIndividualOutSGP = 0;
//        long countNonFaceToFace = riskMatrixList.stream()
//                .filter(riskMatrix -> riskMatrix.verificationType == VerificationType.NON_FACE_TO_FACE)
//                .map(RiskMatrix::getClientId)
//                .count();
//        for (NonIndividual nonIndividual : nonIndividualList) {
//            if ("SGP".equals(nonIndividual.country)) {
//                countNonIndividualInSGP++;
//            } else {
//                countNonIndividualOutSGP++;
//            }
//        }
//        long countIndividualInSGP = 0;
//        long countIndividualOutSGP = 0;
//        for (Individual individual : individualList) {
//            if ("SGP".equals(individual.country)) {
//                countIndividualInSGP++;
//            } else {
//                countIndividualOutSGP++;
//            }
//        }
//        this.getCellByPos(sheet0, "D23").setCellValue(countIndividualInSGP + countNonIndividualInSGP);
//        this.getCellByPos(sheet0, "D24").setCellValue(countIndividualOutSGP + countNonIndividualOutSGP);
//        this.getCellByPos(sheet0, "D25").setCellValue(countNonFaceToFace);
//        // Form 6B-4 //TODO: To calculate average balance
//        this.getCellByPos(sheet0, "D28").setCellValue("");
//        // Form 6B-5 (a)
//        this.printTop5ByAmountIn6B(sheet0, otcList, 32);
//        this.printTop5ByCountIn6B(sheet0, otcList, 37);
//        // Form 6B-5 (b)
//        List<Otc> otcInSGDList = otcList.stream().filter(otc -> otc.fiatCurrency == Currency.SGD).collect(Collectors.toList());
//        this.printTop5ByAmountIn6B(sheet0, otcInSGDList, 43);
//        this.printTop5ByCountIn6B(sheet0, otcInSGDList, 48);
//        // Form 6B-5 (c)
//        List<Otc> otcNotSGDList = otcList.stream().filter(otc -> otc.fiatCurrency != Currency.SGD).collect(Collectors.toList());
//        this.printTop5ByAmountIn6B(sheet0, otcNotSGDList, 54);
//        this.printTop5ByCountIn6B(sheet0, otcNotSGDList, 59);
//        // Form 6B-5 (d)
//        Set<Long> highRiskIds = riskMatrixList.stream().filter(riskMatrix -> riskMatrix.riskLevel == RiskLevel.HIGH).map(RiskMatrix::getClientId).collect(Collectors.toSet());
//        List<Otc> otcHighRiskList = otcList.stream().filter(otc -> highRiskIds.contains(otc.clientId)).collect(Collectors.toList());
//        this.printTop5ByCountIn6B(sheet0, otcHighRiskList, 64); // Need to update when listed trading crypto is more than 5
//        // Form 6B-5 (e)
//        List<TotalByCurrency> top5HeldDPT = cryptoAccountList.stream()
//                .filter(walletAccount -> walletAccount.currency.isCrypto())
//                .collect(Collectors.toMap(
//                        WalletAccount::getCurrency,
//                        x -> {
//                            TotalByCurrency totalByCurrency = new TotalByCurrency();
//                            totalByCurrency.currency = x.currency;
//                            totalByCurrency.totalAmount = x.balance;
//                            return totalByCurrency;
//                        },
//                        (left, right) -> {
//                            left.totalAmount = this.addAmountWithCurrency(right.currency, right.totalAmount, left.totalAmount);
//                            return left;
//                        },
//                        HashMap::new
//                ))
//                .values()
//                .stream()
//                .sorted(Collections.reverseOrder(Comparator.comparing(TotalByCurrency::getTotalAmount)))
//                .limit(5)
//                .collect(Collectors.toList());
//        for (int i = 0; i < top5HeldDPT.size(); i++) {
//            int row = 70 + i;
//            this.getCellByPos(sheet0, "C" + row).setCellValue(top5HeldDPT.get(i).currency.name);
//            this.getCellByPos(sheet0, "D" + row).setCellValue(top5HeldDPT.get(i).totalAmount.toString());
//        }
//        // Form 6B-5 (f)
//        BigDecimal totalAmountHeldDPT = cryptoAccountList.stream()
//                .filter(walletAccount -> walletAccount.currency.isCrypto())
//                .map(walletAccount -> addAmountWithCurrency(walletAccount.currency, walletAccount.balance, ZERO))
//                .reduce(ZERO, BigDecimal::add);
//        this.getCellByPos(sheet0, "D75").setCellValue(totalAmountHeldDPT.toString());
//        // Form 6B-5 (g)
//        List<TotalByCurrency> heldByHighRiskDPT = cryptoAccountList.stream()
//                .filter(walletAccount -> highRiskIds.contains(walletAccount.clientId))
//                .collect(Collectors.toMap(
//                        WalletAccount::getCurrency,
//                        x -> {
//                            TotalByCurrency totalByCurrency = new TotalByCurrency();
//                            totalByCurrency.currency = x.currency;
//                            totalByCurrency.totalAmount = x.balance;
//                            return totalByCurrency;
//                        },
//                        (left, right) -> {
//                            left.totalAmount = this.addAmountWithCurrency(right.currency, right.totalAmount, left.totalAmount);
//                            return left;
//                        },
//                        HashMap::new
//                ))
//                .values()
//                .stream()
//                .sorted(Collections.reverseOrder(Comparator.comparing(TotalByCurrency::getTotalAmount)))
//                .limit(5)
//                .collect(Collectors.toList());
//        for (int i = 0; i < heldByHighRiskDPT.size(); i++) {
//            int row = 76 + i;
//            this.getCellByPos(sheet0, "C" + row).setCellValue(heldByHighRiskDPT.get(i).currency.name);
//            this.getCellByPos(sheet0, "D" + row).setCellValue(heldByHighRiskDPT.get(i).totalAmount.toString());
//        }
//        // Form 6B-6
//        BigDecimal totalAmountToHighRiskCountry = ZERO;
//        int countToHighRiskCountry = 0;
//        BigDecimal totalAmountFromHighRiskCountry = ZERO;
//        int countFromHighRiskCountry = 0;
//        BigDecimal totalAmountHighRiskTransaction = ZERO;
//        int countHighRiskTransaction = 0;
//        for (CryptoTransaction cryptoTransaction : cryptoTransactionList) {
//            if (highRiskCountryClientIds.contains(cryptoTransaction.clientId)) {
//                if (cryptoTransaction.type == CryptoTransactionType.DEPOSIT) {
//                    countFromHighRiskCountry++;
//                    totalAmountFromHighRiskCountry = addAmountWithCurrency(cryptoTransaction.currency, cryptoTransaction.amount, totalAmountFromHighRiskCountry);
//                } else if (cryptoTransaction.type == CryptoTransactionType.WITHDRAW) {
//                    countToHighRiskCountry++;
//                    totalAmountToHighRiskCountry = addAmountWithCurrency(cryptoTransaction.currency, cryptoTransaction.amount, totalAmountToHighRiskCountry);
//                }
//            }
//            if (highRiskIds.contains(cryptoTransaction.clientId)) {
//                countHighRiskTransaction++;
//                totalAmountHighRiskTransaction = addAmountWithCurrency(cryptoTransaction.currency, cryptoTransaction.amount, totalAmountHighRiskTransaction);
//            }
//        }
//        this.getCellByPos(sheet0, "C83").setCellValue(totalAmountToHighRiskCountry.toString());
//        this.getCellByPos(sheet0, "E83").setCellValue(countToHighRiskCountry);
//        this.getCellByPos(sheet0, "C84").setCellValue(totalAmountFromHighRiskCountry.toString());
//        this.getCellByPos(sheet0, "E84").setCellValue(countFromHighRiskCountry);
//        // Form 6B-7 (a) No PEP client
//        this.getCellByPos(sheet0, "C87").setCellValue(0);
//        this.getCellByPos(sheet0, "D87").setCellValue("0.00");
//        this.getCellByPos(sheet0, "E87").setCellValue(0);
//        // Form 6B-7 (b)
//        this.getCellByPos(sheet0, "C88").setCellValue(highRiskIds.size());
//        this.getCellByPos(sheet0, "D88").setCellValue(totalAmountHighRiskTransaction.toString());
//        this.getCellByPos(sheet0, "E88").setCellValue(countHighRiskTransaction);
//        /*
//                SHEET 1 OTC Transaction
//         */
//        XlsxGenerator
//                .records(workbook, otcList, OtcReport.class)
//                .valueHandler((key, record) -> {
//                    Otc otc = (Otc) record;
//                    if ("rateToSGD".equals(key)) {
//                        return getRateToSGD(otc.fiatCurrency);
//                    }
//                    return FieldValue.empty();
//                })
//                .genSheet("OTC Transaction");
//        /*
//                SHEET 2 Crypto Transaction
//         */
//        XlsxGenerator
//                .records(workbook, cryptoTransactionList, CryptoTransactionReport.class)
//                .genSheet("Crypto Transaction");
//        /*
//                SHEET 3 Non-individual Client
//         */
//        XlsxGenerator
//                .records(workbook, nonIndividualList, NonIndividualReport.class)
//                .genSheet("Non-individual Client");
//        /*
//                SHEET 4 Individual Client
//         */
//        XlsxGenerator
//                .records(workbook, individualList, IndividualReport.class)
//                .genSheet("Individual Client");
//        /*
//                SHEET 5 Risk Matrix
//         */
//        XlsxGenerator
//                .records(workbook, riskMatrixList, RiskMatrixReport.class)
//                .genSheet("Risk Matrix");
//    }
//
//    private FieldValue getRateToSGD(Currency currency) {
//        switch (currency) {
//            case SGD:
//                return new FieldValue<>(BigDecimal.ONE);
//            case USD:
//                return new FieldValue<>(rateFromUSD);
//            case CNY:
//                return new FieldValue<>(rateFromCNY);
//            default:
//                return FieldValue.empty();
//        }
//    }
//
//    private BigDecimal addPaymentAmount(PaymentTransaction paymentTransaction, BigDecimal totalAmount) {
//        return addAmountWithCurrency(paymentTransaction.requestCurrency, paymentTransaction.totalAmount, totalAmount);
//    }
//
//    private BigDecimal addOtcAmount(Otc otc, BigDecimal totalAmount) {
//        return addAmountWithCurrency(otc.fiatCurrency, otc.fiatAmount, totalAmount);
//    }
//
//    private BigDecimal addAmountWithCurrency(Currency currency, BigDecimal amount, BigDecimal originalAmount) {
//        switch (currency) {
//            case SGD:
//                return originalAmount.add(amount);
//            case USD:
//                return originalAmount.add(amount.multiply(rateFromUSD).setScale(Currency.SGD.exponent, RoundingMode.HALF_UP));
//            case EUR:
//                return originalAmount.add(amount.multiply(rateFromEUR).setScale(Currency.SGD.exponent, RoundingMode.HALF_UP));
//            case AUD:
//                return originalAmount.add(amount.multiply(rateFromAUD).setScale(Currency.SGD.exponent, RoundingMode.HALF_UP));
//            case JPY:
//                return originalAmount.add(amount.multiply(rateFromJPY).setScale(Currency.SGD.exponent, RoundingMode.HALF_UP));
//            case HKD:
//                return originalAmount.add(amount.multiply(rateFromHKD).setScale(Currency.SGD.exponent, RoundingMode.HALF_UP));
//            case GBP:
//                return originalAmount.add(amount.multiply(rateFromGBP).setScale(Currency.SGD.exponent, RoundingMode.HALF_UP));
//            case CNY:
//                return originalAmount.add(amount.multiply(rateFromCNY).setScale(Currency.SGD.exponent, RoundingMode.HALF_UP));
//            case ETH:
//                return originalAmount.add(amount.multiply(rateFromETH).setScale(Currency.SGD.exponent, RoundingMode.HALF_UP));
//            case BTC:
//                return originalAmount.add(amount.multiply(rateFromBTC).setScale(Currency.SGD.exponent, RoundingMode.HALF_UP));
//            case TRX:
//                return originalAmount.add(amount.multiply(rateFromTRX).setScale(Currency.SGD.exponent, RoundingMode.HALF_UP));
//            case USDT:
//                return originalAmount.add(amount.multiply(rateFromUSDT).setScale(Currency.SGD.exponent, RoundingMode.HALF_UP));
//            case USDC:
//                return originalAmount.add(amount.multiply(rateFromUSDC).setScale(Currency.SGD.exponent, RoundingMode.HALF_UP));
//            default:
//                throw new ValidationException(String.format("Can't convert transaction currency %s to SGD", currency));
//        }
//    }
//
//    private void printTop5ByAmountIn6B(XSSFSheet sheet, List<Otc> otcListToSort, int startedRow) {
//        List<TotalByCurrency> otcNotSGDSortedByAmount = getUnsortedStream(otcListToSort)
//                .sorted(Collections.reverseOrder(Comparator.comparing(TotalByCurrency::getTotalAmount)))
//                .limit(5)
//                .collect(Collectors.toList());
//        this.print6bTop5(sheet, otcNotSGDSortedByAmount, startedRow);
//    }
//
//    private void printTop5ByCountIn6B(XSSFSheet sheet, List<Otc> otcListToSort, int startedRow) {
//        List<TotalByCurrency> otcNotSGDSortedByAmount = getUnsortedStream(otcListToSort)
//                .sorted(Collections.reverseOrder(Comparator.comparing(TotalByCurrency::getTotalCount)))
//                .limit(5)
//                .collect(Collectors.toList());
//        this.print6bTop5(sheet, otcNotSGDSortedByAmount, startedRow);
//    }
//
//    private Stream<TotalByCurrency> getUnsortedStream(List<Otc> otcListToSort) {
//        return otcListToSort.stream()
//                .collect(Collectors.toMap(
//                        Otc::getCryptoCurrency,
//                        x -> {
//                            TotalByCurrency totalByCurrency = new TotalByCurrency();
//                            totalByCurrency.currency = x.cryptoCurrency;
//                            totalByCurrency.totalAmount = x.fiatAmount;
//                            return totalByCurrency;
//                        },
//                        (left, right) -> {
//                            left.totalCount++;
//                            left.totalAmount = this.addAmountWithCurrency(right.currency, right.totalAmount, left.totalAmount);
//                            return left;
//                        },
//                        HashMap::new
//                ))
//                .values()
//                .stream();
//    }
//
//    private void print6bTop5(XSSFSheet sheet, List<TotalByCurrency> otcNotSGDSortedByAmount, int startedRow) {
//        for (int i = 0; i < otcNotSGDSortedByAmount.size(); i++) {
//            int row = startedRow + i;
//            this.getCellByPos(sheet, "C" + row).setCellValue(otcNotSGDSortedByAmount.get(i).currency.name);
//            this.getCellByPos(sheet, "D" + row).setCellValue(otcNotSGDSortedByAmount.get(i).totalAmount.toString());
//            this.getCellByPos(sheet, "E" + row).setCellValue(otcNotSGDSortedByAmount.get(i).totalCount);
//        }
//    }

    private XSSFCell getCellByPos(XSSFSheet sheet, String position) {
        CellReference cr = new CellReference(position);
        return sheet.getRow(cr.getRow()).getCell(cr.getCol());
    }

    public byte[] toByteArray() throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        this.workbook.write(stream);
        this.workbook.close();
        return stream.toByteArray();
    }

    public ResponseEntity<ByteArrayResource> toResponseEntity(String filename) throws IOException {
        byte[] bytes = toByteArray();
        HttpHeaders header = new HttpHeaders();
        header.setContentType(new MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        header.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename + ".xlsx");
        return new ResponseEntity<>(new ByteArrayResource(bytes), header, HttpStatus.CREATED);
    }

    @Data
    private static class TotalCountByCountry {

        public long totalCount;
        public String country;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TotalCountByCountry key = (TotalCountByCountry) o;
            return Objects.equal(country, key.country);
        }

        @Override
        public int hashCode() { return Objects.hashCode(country);}

    }

    @Data
    private static class TotalCountByMerchant {

        public Long clientId;
        public long totalCount;
        public String clientName;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TotalCountByMerchant key = (TotalCountByMerchant) o;
            return Objects.equal(clientId, key.clientId);
        }

        @Override
        public int hashCode() { return Objects.hashCode(clientId);}

    }

    @Data
    private static class TotalByCurrency {

        public Currency currency;
        public long totalCount;
        public BigDecimal totalAmount;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TotalByCurrency key = (TotalByCurrency) o;
            return Objects.equal(currency, key.currency);
        }

        @Override
        public int hashCode() { return Objects.hashCode(currency);}

    }

}
