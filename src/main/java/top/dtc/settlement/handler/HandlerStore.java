package top.dtc.settlement.handler;

import top.dtc.common.util.StringUtils;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class HandlerStore {

    private static final Map<Class<?>, List<Field>> fieldMap = new HashMap<>();
    private static final Map<String, SimpleDateFormat> dateFormatMap = new HashMap<>();
    private static final Map<Class<?>, Map<Field, RecordField>> recordFieldMap = new HashMap<>();

    private static void init(Class<?> clazz) {
        List<Field> fieldList = Arrays.stream(clazz.getDeclaredFields())
                .filter(f -> f.getAnnotation(RecordField.class) != null)
                .sorted(Comparator.comparingInt(f -> f.getAnnotation(RecordField.class).order()))
                .collect(Collectors.toList());
        Map<Field, RecordField> map = new HashMap<>();
        recordFieldMap.put(clazz, map);
        fieldList.forEach(field -> {
            field.setAccessible(true);
            RecordField recordField = field.getAnnotation(RecordField.class);
            map.put(field, recordField);
            String format = recordField.format();
            if (!StringUtils.isBlank(format) && !dateFormatMap.containsKey(format)) {
                SimpleDateFormat dateFormat = new SimpleDateFormat(format);
                dateFormatMap.put(format, dateFormat);
            }
        });
        fieldMap.put(clazz, fieldList);
    }

    protected static List<Field> getFieldList(Class<?> clazz) {
        if (!fieldMap.containsKey(clazz)) {
            init(clazz);
        }
        return fieldMap.get(clazz);
    }

    protected static RecordField getRecordField(Class<?> clazz, Field field) {
        return recordFieldMap.get(clazz).get(field);
    }

    protected static SimpleDateFormat getDateFormat(String format) {
        return dateFormatMap.get(format);
    }

}
