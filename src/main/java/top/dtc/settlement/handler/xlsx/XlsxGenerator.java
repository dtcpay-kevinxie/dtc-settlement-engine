package top.dtc.settlement.handler.xlsx;

import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.*;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTTableStyleInfo;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import top.dtc.common.enums.Currency;
import top.dtc.common.util.StringUtils;
import top.dtc.settlement.handler.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static top.dtc.settlement.constant.DateConstant.FORMAT.DATE;

public class XlsxGenerator<T> {

    private Class<?> clazz;
    private List<?> records;
    private XSSFWorkbook workbook;
    private Map<String, CellStyle> cellStyleMap = new HashMap<>();
    private BiFunction<String, Object, FieldValue<Object>> valueHandler = null;

    private XlsxGenerator() {
    }

    public XlsxGenerator<?> moreRecords(List<?> records, Class<?> clazz) {
        XlsxGenerator<?> processor = new XlsxGenerator<>();
        processor.clazz = clazz;
        processor.records = records;
        processor.workbook = this.workbook;
        return processor;
    }

    public static <T> XlsxGenerator<T> records(List<?> records, Class<T> clazz) {
        return records(new XSSFWorkbook(), records, clazz);
    }
    public static <T> XlsxGenerator<T> records(XSSFWorkbook workbook, List<?> records, Class<T> clazz) {
        XlsxGenerator<T> processor = new XlsxGenerator<>();
        processor.clazz = clazz;
        processor.records = records;
        processor.workbook = workbook;
        return processor;
    }

    public XlsxGenerator<T> valueHandler(BiFunction<String, Object, FieldValue<Object>> valueHandler) {
        this.valueHandler = valueHandler;
        return this;
    }

    public XlsxGenerator<T> genSheet(String name) throws IllegalAccessException {
        return this.genSheet(name, false);
    }

    public XlsxGenerator<T> genSheet(String name, boolean editEnabled) throws IllegalAccessException {
        List<RecordField> recordFieldList = XlsxGeneratorStore.init(this.clazz);

        // Gathering map columns
        Map<RecordField, Set<String>> mapColumns = XlsxGeneratorUtils.genMapColumns(this.clazz, this.records, recordFieldList);

        XSSFSheet sheet = this.workbook.createSheet(name);
        XSSFRow titleRow = sheet.createRow(0);
        int nextCol = 0;
        for (RecordField recordField : recordFieldList) {
            if (recordField.type() == RecordFieldType.AMOUNT_MAP) {
                for (String title : mapColumns.get(recordField)) {
                    XSSFCell cell = titleRow.createCell(nextCol++);
                    cell.setCellValue(title);
                }
                continue;
            }
            XSSFCell cell = titleRow.createCell(nextCol++);
            cell.setCellValue(recordField.title());
        }

        for (int i = 0; i < records.size(); i++) {
            Object record = records.get(i);
            XSSFRow row = sheet.createRow(i + 1);
            Map<String, Object> valueMap = XlsxGeneratorUtils.getValueMap(this.clazz, recordFieldList, record);
            nextCol = 0;
            for (RecordField recordField : recordFieldList) {
                if (recordField.type() == RecordFieldType.AMOUNT_MAP) {
                    Object obj = XlsxGeneratorUtils.getValueFromMap(clazz, valueMap, recordField);
                    Map<String, Object> map = (Map<String, Object>) obj;
                    for (String key : mapColumns.get(recordField)) {
                        XSSFCell cell = row.createCell(nextCol++);
                        Object value = map.get(key);
                        double amount = this.parseAmount(value);
                        Currency currency = (Currency) XlsxGeneratorUtils.getValueFromMap(valueMap, recordField.currencyPath());
                        if (currency == null) {
                            cell.setCellValue(amount);
                        } else if (currency.exponent > 0) {
                            amount /= this.getAmountRatio(currency);
                            this.setCellFormat(cell, this.getAmountFormat(currency));
                        }
                        cell.setCellValue(amount);
                    }
                    continue;
                }
                XSSFCell cell = row.createCell(nextCol++);
                this.fillCell(this.clazz, recordField, record, valueMap, cell);
            }
        }

        for (int i = 0; i < nextCol; i++) {
            sheet.autoSizeColumn(i);
            sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 500);
        }

        this.createTable(sheet, 0, 0, this.records.size(), nextCol - 1, name, true, editEnabled);

        return this;
    }

    public XlsxGenerator<T> genInfoSheet(Object record) throws IllegalAccessException {
        return this.genInfoSheet(record, false);
    }

    public XlsxGenerator<T> genInfoSheet(Object record, boolean editEnabled) throws IllegalAccessException {
        List<RecordField> recordFieldList = XlsxGeneratorStore.init(record.getClass());

        XSSFSheet sheet = this.workbook.createSheet("Gen Info");
        XSSFRow titleRow = sheet.createRow(0);
        XSSFCell titleCell0 = titleRow.createCell(0);
        titleCell0.setCellValue("Parameter");
        XSSFCell titleCell1 = titleRow.createCell(1);
        titleCell1.setCellValue("Value");

        Map<String, Object> valueMap = XlsxGeneratorUtils.getValueMap(record.getClass(), recordFieldList, record);

        for (int i = 0; i < recordFieldList.size(); i++) {
            RecordField recordField = recordFieldList.get(i);
            XSSFRow row = sheet.createRow(i + 1);
            XSSFCell parameterCell = row.createCell(0);
            parameterCell.setCellValue(recordField.title());

            XSSFCell valueCell = row.createCell(1);
            this.fillCell(record.getClass(), recordField, record, valueMap, valueCell);
        }

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);

        this.createTable(sheet, 0, 0, recordFieldList.size(), 1, "Gen Info", false, editEnabled);

        XSSFRow dateRow = sheet.createRow(recordFieldList.size() + 2);
        XSSFCell dateCell = dateRow.createCell(0);
        dateCell.setCellValue("Generated at " + OffsetDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME));
        XSSFFont italicFont = this.workbook.createFont();
        italicFont.setItalic(true);
        XSSFCellStyle italicCellStyle = this.workbook.createCellStyle();
        italicCellStyle.setFont(italicFont);
        dateCell.setCellStyle(italicCellStyle);

        return this;
    }

    private void createTable(XSSFSheet sheet, int row0, int col0, int row1, int col1, String name, boolean autoFilter, boolean editEnabled) {
        if (!editEnabled) {
            lock(sheet);
        }
        if (row1 - row0 < 2)  {
            row1 = row0 + 1;
        }
        AreaReference reference = new AreaReference(
                new CellReference(row0, col0),
                new CellReference(row1, col1),
                SpreadsheetVersion.EXCEL2007
        );
        XSSFTable table = sheet.createTable(reference);
        table.setName(name);
        if (autoFilter) {
            table.getCTTable().addNewAutoFilter();
        }
        CTTableStyleInfo tableStyleInfo = table.getCTTable().addNewTableStyleInfo();
        tableStyleInfo.setName("TableStyleMedium15");
        tableStyleInfo.setShowColumnStripes(false);
        tableStyleInfo.setShowRowStripes(true);
    }

    private void fillCell(Class<?> clazz, RecordField recordField, Object record, Map<String, Object> valueMap, XSSFCell cell) {
        Field field = XlsxGeneratorStore.getFinalField(clazz, recordField);
        Object obj;
        FieldValue<Object> optional;
        if (this.valueHandler != null) {
            optional = this.valueHandler.apply(XlsxGeneratorStore.getPath(clazz, recordField), record);
            obj = optional.orElseGet(() -> XlsxGeneratorUtils.getValueFromMap(clazz, valueMap, recordField));
        } else {
            obj = XlsxGeneratorUtils.getValueFromMap(clazz, valueMap, recordField);
        }

        if (!StringUtils.isBlank(recordField.format())) {
            this.setCellFormat(cell, recordField.format());
        }
        if (obj != null) {
            if (XlsxGeneratorUtils.isNumeric(field)) {
                switch (recordField.type()) {
                    case AMOUNT: {
                        double amount = this.parseAmount(obj);
                        Currency currency = (Currency) XlsxGeneratorUtils.getValueFromMap(valueMap, recordField.currencyPath());
                        if (currency == null) {
                           cell.setCellValue(amount);
                        } else if (currency.exponent > 0) {
                            this.setCellFormat(cell, this.getAmountFormat(currency));
                        }
                        cell.setCellValue(amount);
                        break;
                    }
                    case PERCENTAGE:
                        double amount = Double.parseDouble(obj.toString());
                        cell.setCellValue(amount * 100);
                        break;
                    case BOOLEAN_NUMBER:
                        int number = (Integer) obj;
                        if (number == 0) {
                            cell.setCellValue("No");
                        } else if (number == 1) {
                            cell.setCellValue("Yes");
                        }
                        break;
                    default:
                        cell.setCellValue(Double.parseDouble(obj.toString()));
                }
                // TODO long object to amount, percent
            } else if (obj instanceof LocalDateTime) {
                cell.setCellValue((LocalDateTime) obj);
                if (StringUtils.isBlank(recordField.format())) {
                    this.setCellFormat(cell, DATE);
                }
            } else if (obj instanceof LocalDate) {
                cell.setCellValue((LocalDate) obj);
                if (StringUtils.isBlank(recordField.format())) {
                    this.setCellFormat(cell, DATE);
                }
            } else if (obj instanceof LocalTime) {
                throw new RuntimeException("LocalTime does not supported by cell.setCellValue");
            } else if (Boolean.class == field.getType() || Boolean.TYPE == field.getType()) {
                cell.setCellValue((Boolean) obj);
            } else if (recordField.type() == RecordFieldType.COLLECTION) {
                Collection<?> collection = (Collection<?>) obj;
                if (!collection.isEmpty()) {
                    cell.setCellValue(collection.stream()
                            .map(Object::toString)
                            .collect(Collectors.joining(",")));
                }
            } else {
                cell.setCellValue(obj.toString());
            }
        }
    }

    private String getAmountFormat(Currency currency) {
        return currency.exponent == 0 ? "0" : "0.00";
    }

    private double getAmountRatio(Currency currency) {
        return currency.exponent == 0 ? 1.0 : 100.0;
    }

    private double parseAmount(Object obj) {
        double amount = 0;
        if (obj != null) {
            String amountStr = obj.toString();
            if (!StringUtils.isBlank(amountStr)) {
                amount = Double.parseDouble(obj.toString());
            }
        }
        return amount;
    }

    private void setCellFormat(XSSFCell cell, String format) {
        CellStyle cellStyle = cellStyleMap.get(format);
        if (cellStyle == null) {
            cellStyle = this.workbook.createCellStyle();
            cellStyle.setDataFormat(this.workbook.createDataFormat().getFormat(format));
            cellStyleMap.put(format, cellStyle);
        }
        cell.setCellStyle(cellStyle);
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
        header.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename + " " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx");

        return new ResponseEntity<>(new ByteArrayResource(bytes), header, HttpStatus.CREATED);
    }

    public static void lock(XSSFSheet sheet) {
        sheet.protectSheet("");
    }

}
