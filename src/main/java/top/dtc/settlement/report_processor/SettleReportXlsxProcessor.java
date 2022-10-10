package top.dtc.settlement.report_processor;

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
import top.dtc.addon.data_processor.xlsx.XlsxProcessor;
import top.dtc.common.enums.Brand;
import top.dtc.data.core.model.Country;
import top.dtc.data.finance.model.PaymentFeeStructure;
import top.dtc.data.finance.model.Reserve;
import top.dtc.data.finance.model.Settlement;
import top.dtc.settlement.report_processor.vo.SettlementTransactionReport;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;

public class SettleReportXlsxProcessor {

    private XSSFWorkbook workbook = null;
    private HashMap<Brand, List<SettlementTransactionReport>> transactionMap = null;
    private Settlement settlement = null;
    private Reserve reserve = null;
    private String txnFeeConfig = "";
    private String perSaleFeeConfig = "";
    private String perRefundFeeConfig = "";
    private String perChargebackFeeConfig = "";
    private Country country = null;

    public static SettleReportXlsxProcessor build(
            Settlement settlement,
            Reserve reserve,
            HashMap<Brand, List<SettlementTransactionReport>> transactionMap,
            HashMap<Brand, List<PaymentFeeStructure>> feeStructureMap,
            Country country
    ) throws IOException, IllegalAccessException {
        SettleReportXlsxProcessor processor = new SettleReportXlsxProcessor();
        processor.workbook = new XSSFWorkbook(SettleReportXlsxProcessor.class.getResourceAsStream("/xlsx-templates/settlement-report.xlsx"));
        processor.settlement = settlement;
        processor.reserve = reserve;
        processor.transactionMap = transactionMap;
        processor.country = country;
        for (Brand brand : feeStructureMap.keySet()) {
            List<PaymentFeeStructure> feeStructures = feeStructureMap.get(brand);
            feeStructures.forEach(paymentFeeStructure -> {
                processor.txnFeeConfig += String.format("%s %s (%s%%)\n", brand, paymentFeeStructure.feeType.desc, paymentFeeStructure.mdr.multiply(new BigDecimal(100)));
                processor.perSaleFeeConfig += String.format("%s %s (%s)\n", brand, paymentFeeStructure.feeType.desc, paymentFeeStructure.saleFee);
                processor.perRefundFeeConfig += String.format("%s %s (%s)\n", brand, paymentFeeStructure.feeType.desc, paymentFeeStructure.refundFee);
                processor.perChargebackFeeConfig += String.format("%s %s (%s)\n", brand, paymentFeeStructure.feeType.desc, paymentFeeStructure.chargebackFee);
            });
        }
        processor.fill();
        return processor;
    }

    private void fill() throws IllegalAccessException {
        CellStyle percentCellStyle = this.workbook.createCellStyle();
        percentCellStyle.setDataFormat(this.workbook.createDataFormat().getFormat("0%"));
        /*
                SHEET 0 Summary
         */
        XSSFSheet sheet0 = this.workbook.getSheetAt(0);
        XlsxProcessor.lock(sheet0);
        this.workbook.setSheetName(0, "Summary");
        // Summary Report
        this.getCellByPos(sheet0, "B3").setCellValue(settlement.merchantId);
        this.getCellByPos(sheet0, "E3").setCellValue(settlement.merchantName);
        this.getCellByPos(sheet0, "B4").setCellValue(settlement.currency.name);
        this.getCellByPos(sheet0, "A6").setCellValue("Settlement-" + settlement.id);
        this.getCellByPos(sheet0, "D6").setCellValue(settlement.settleFinalAmount.doubleValue());
        if (reserve != null) {
            this.getCellByPos(sheet0, "A7").setCellValue("Reserve-" + reserve.id);
            this.getCellByPos(sheet0, "D7").setCellValue(reserve.totalAmount.negate().doubleValue());
        }
        this.getCellByPos(sheet0, "D8").setCellValue(settlement.settleFinalAmount.add(reserve.totalAmount).doubleValue());
        /*
                SHEET 1 Settlement Report
         */
        XSSFSheet sheet1 = this.workbook.getSheetAt(1);
        XlsxProcessor.lock(sheet1);
        this.workbook.setSheetName(1, String.format("Settlement-%s", settlement.id));
        // Settlement Report Header
        this.getCellByPos(sheet1, "E3").setCellValue(settlement.id);
        this.getCellByPos(sheet1, "E4").setCellValue(settlement.invoiceNumber);
        this.getCellByPos(sheet1, "E5").setCellValue(settlement.currency.name);
        // Settlement Info
        this.getCellByPos(sheet1, "B7").setCellValue(settlement.cycleStartDate);
        this.getCellByPos(sheet1, "D7").setCellValue(settlement.cycleEndDate);
        this.getCellByPos(sheet1, "F7").setCellValue(settlement.scheduleType.desc);
        this.getCellByPos(sheet1, "B8").setCellValue(settlement.settleDate);

        // Transaction Report
        this.getCellByPos(sheet1, "C10").setCellValue(settlement.saleCount);
        this.getCellByPos(sheet1, "E10").setCellValue(settlement.saleAmount.doubleValue());
        this.getCellByPos(sheet1, "C11").setCellValue(settlement.refundCount);
        this.getCellByPos(sheet1, "E11").setCellValue(settlement.refundAmount.doubleValue());
        this.getCellByPos(sheet1, "C12").setCellValue(settlement.chargebackCount);
        this.getCellByPos(sheet1, "E12").setCellValue(settlement.chargebackAmount.doubleValue());
        this.getCellByPos(sheet1, "E13").setCellValue(settlement.adjustmentAmount.doubleValue());
        BigDecimal totalTxnAmount = settlement.saleAmount.add(settlement.refundAmount).add(settlement.chargebackAmount).add(settlement.adjustmentAmount);
        this.getCellByPos(sheet1, "E13").setCellValue(totalTxnAmount.doubleValue());

        // Service Charge
        this.getCellByPos(sheet1, "C16").setCellValue(txnFeeConfig);
        this.getCellByPos(sheet1, "E16").setCellValue(settlement.mdrFee.doubleValue());
        this.getCellByPos(sheet1, "C17").setCellValue(perSaleFeeConfig);
        this.getCellByPos(sheet1, "E17").setCellValue(settlement.saleProcessingFee.doubleValue());
        this.getCellByPos(sheet1, "C18").setCellValue(perRefundFeeConfig);
        this.getCellByPos(sheet1, "E18").setCellValue(settlement.refundProcessingFee.doubleValue());
        this.getCellByPos(sheet1, "C19").setCellValue(perChargebackFeeConfig);
        this.getCellByPos(sheet1, "E19").setCellValue(settlement.chargebackProcessingFee.doubleValue());
        this.getCellByPos(sheet1, "E20").setCellValue(settlement.annualFee.doubleValue());
        this.getCellByPos(sheet1, "E21").setCellValue(settlement.monthlyFee.doubleValue());
        BigDecimal totalCharge = settlement.mdrFee
                .add(settlement.saleProcessingFee)
                .add(settlement.refundProcessingFee)
                .add(settlement.chargebackProcessingFee)
                .add(settlement.annualFee)
                .add(settlement.monthlyFee);
        this.getCellByPos(sheet1, "E22").setCellValue(totalCharge.doubleValue());
        //Tax
        this.getCellByPos(sheet1, "A24").setCellValue(String.format("GST/VAT (%s)", country.codeAlpha3));
        this.getCellByPos(sheet1, "C24").setCellValue(country.gst.toString());
        this.getCellByPos(sheet1, "E24").setCellValue(settlement.vatAmount.doubleValue());
//        this.getCellByPos(sheet1, "A25").setCellValue("Withholding Tax");
//        this.getCellByPos(sheet1, "C25").setCellValue(0.03);
//        this.getCellByPos(sheet1, "E25").setCellValue(settlement.vatAmount.doubleValue());
        BigDecimal totalTax = settlement.vatAmount;
        this.getCellByPos(sheet1, "E26").setCellValue(totalTax.doubleValue());
        this.getCellByPos(sheet1, "E27").setCellValue(settlement.settleFinalAmount.doubleValue());
        /*
                SHEET 2 Reserve
         */
        if (reserve != null) {
            XSSFSheet sheet2 = this.workbook.getSheetAt(2);
            XlsxProcessor.lock(sheet2);
            this.workbook.setSheetName(2, String.format("Reserve-%s", reserve.id));
            // Reserve Header
            this.getCellByPos(sheet2, "B3").setCellValue(reserve.id);
            this.getCellByPos(sheet2, "D3").setCellValue(reserve.currency.name);
            //Reserve Held
            this.getCellByPos(sheet2, "B5").setCellValue(reserve.reservePeriod);
            this.getCellByPos(sheet2, "D5").setCellValue(reserve.dateToRelease);
            this.getCellByPos(sheet2, "C6").setCellValue(reserve.reserveRate.doubleValue());
            this.getCellByPos(sheet2, "C7").setCellValue(reserve.totalAmount.negate().doubleValue());
        }
    }

    private void generateTransactionsByBrand() throws IllegalAccessException {
        /*
                SHEET Transaction List
         */
        for (Brand brand : transactionMap.keySet()) {
            XlsxProcessor
                    .records(workbook, transactionMap.get(brand), SettlementTransactionReport.class)
                    .genSheet(String.format("%s Transactions", brand.name));
        }
    }

    private XSSFCell getCellByPos(XSSFSheet sheet, String position) {
        CellReference cr = new CellReference(position);
        return sheet.getRow(cr.getRow()).getCell(cr.getCol());
    }

    public void toFile(String path) throws IOException {
        OutputStream fileOut = new FileOutputStream(path);
        this.workbook.write(fileOut);
        this.workbook.close();
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

}
