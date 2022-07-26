package top.dtc.settlement.handler.xlsx;

import org.apache.poi.ss.usermodel.*;
import top.dtc.common.util.StringUtils;
import top.dtc.settlement.handler.RecordField;
import top.dtc.settlement.handler.XlsxReaderStore;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class XlsxReader {

    private static final DataFormatter dataFormatter = new DataFormatter();
    private static final BigDecimal BD_100 = new BigDecimal(100);

    public static <T> List<T> readListFromSheet(Sheet sheet, Class<T> clazz, int startRowNum) throws IllegalAccessException, ParseException, InstantiationException, NoSuchMethodException, InvocationTargetException {
        int rowCount = sheet.getLastRowNum();
        List<T> list = new ArrayList<>();
        for (int rowNum = startRowNum; rowNum <= rowCount; rowNum++) {
            T t = readRow(sheet, rowNum, clazz);
            if (t == null) {
                break;
            }
            list.add(t);
        }
        return list;
    }

    public static <T> T readRow(Sheet sheet, int rowNum, Class<T> clazz) throws IllegalAccessException, InstantiationException, ParseException, NoSuchMethodException, InvocationTargetException {
        Row row = sheet.getRow(rowNum);
        if (isRowEmpty(row)) {
            return null;
        }
        T object = clazz.getDeclaredConstructor().newInstance();
        for (Field field : XlsxReaderStore.getFieldList(clazz)) {
            RecordField recordField = XlsxReaderStore.getRecordField(clazz, field);
            if (!recordField.ignoredFromExcel()) {
                String data = recordField.fixedValue();
                if (data.isEmpty()) {
                    Cell cell = row.getCell(recordField.order());
                    data = dataFormatter.formatCellValue(cell);
                }
                writeField(field, recordField, object, data);
            }
        }
        return object;
    }

    private static void writeField(Field field, RecordField recordField, Object object, String data) throws IllegalAccessException, ParseException {
        if (StringUtils.isBlank(data)) {
            return;
        }
        if (BigDecimal.class == field.getType()) {
            if (data.contains("%")) {
                field.set(object, new BigDecimal(data.replaceAll("%", "")).divide(BD_100, RoundingMode.HALF_UP));
            } else {
                field.set(object, new BigDecimal(data));
            }
        } else if (Boolean.class == field.getType() || Boolean.TYPE == field.getType()) {
            field.set(object, Boolean.parseBoolean(data));
        } else if (Byte.class == field.getType() || Byte.TYPE == field.getType()) {
            field.set(object, Byte.parseByte(data));
        } else if (Short.class == field.getType() || Short.TYPE == field.getType()) {
            field.set(object, Short.parseShort(data));
        } else if (Integer.class == field.getType() || Integer.TYPE == field.getType()) {
            field.set(object, Integer.parseInt(data));
        } else if (Long.class == field.getType() || Long.TYPE == field.getType()) {
            field.set(object, Long.parseLong(data));
        } else if (Float.class == field.getType() || Float.TYPE == field.getType()) {
            field.set(object, Float.parseFloat(data));
        } else if (Double.class == field.getType() || Double.TYPE == field.getType()) {
            field.set(object, Double.parseDouble(data));
        } else if (String.class == field.getType()) {
            field.set(object, data);
        } else if (Date.class == field.getType()) {
            SimpleDateFormat dateFormat = XlsxReaderStore.getDateFormat(recordField.format());
            if (dateFormat != null) {
                field.set(object, dateFormat.parse(data));
            }
        }
    }

    private static boolean isRowEmpty(Row row) {
        return row == null || (
                (row.getCell(0) == null || row.getCell(0).getCellType() == CellType.BLANK)
                        && (row.getCell(1) == null || row.getCell(1).getCellType() == CellType.BLANK)
                        && (row.getCell(2) == null || row.getCell(2).getCellType() == CellType.BLANK)
        );
    }

}
