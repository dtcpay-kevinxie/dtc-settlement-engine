package top.dtc.settlement.handler;

import top.dtc.common.util.StringUtils;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

public class XlsxGeneratorUtils {

    public static boolean isJavaLang(Class<?> check) {

        Package p = check.getPackage();

        if (p == null) // default package is package for users classes
            return false;

        String title = p.getImplementationTitle();
        if (title == null)// no title -> class not from Oracle
            return false;

        // System.out.println(p.getImplementationVendor());
        // System.out.println(p.getImplementationTitle());
        return title.equals("Java Runtime Environment");
    }

    public static Map<String, Object> getValueMap(Class<?> clazz, List<RecordField> recordFieldList, Object record) throws IllegalAccessException {
        Map<String, List<Field>> pathFieldMap = XlsxGeneratorStore.getPathFieldMap(clazz);
        Map<String, Object> valueMap = new HashMap<>();
        for (Map.Entry<String, List<Field>> entry : pathFieldMap.entrySet()) {
            String path = entry.getKey();
            List<Field> fieldList = entry.getValue();
            Object obj = null;
            for (Field field : fieldList) {
                if (obj == null) {
                    try {
                        obj = field.get(record);
                    } catch (IllegalArgumentException e) {
                    }
                    if (obj == null) {
                        break;
                    }
                    continue;
                }
                obj = field.get(obj);
                if (obj == null) {
                    break;
                }
            }
            if (StringUtils.isBlank(path)) {
                path = fieldList.get(fieldList.size() - 1).getName();
            }
            valueMap.put(path, obj);
        }
//        for (Field field : clazz.getDeclaredFields()) {
//            if (!valueMap.containsKey(field.getName())) {
//                valueMap.put(field.getName(), field.get(record));
//            }
//        }
        return valueMap;
    }

    public static Object getValueFromMap(Map<String, Object> valueMap, String path) {
        return valueMap.get(path);
    }

    public static Object getValueFromMap(Class<?> clazz, Map<String, Object> valueMap, RecordField recordField) {
        return getValueFromMap(valueMap, XlsxGeneratorStore.getPath(clazz, recordField));
    }

    public static boolean isNumeric(Field field) {
        return BigDecimal.class == field.getType()
                || Byte.class == field.getType() || Byte.TYPE == field.getType()
                || Short.class == field.getType() || Short.TYPE == field.getType()
                || Integer.class == field.getType() || Integer.TYPE == field.getType()
                || Long.class == field.getType() || Long.TYPE == field.getType()
                || Float.class == field.getType() || Float.TYPE == field.getType()
                || Double.class == field.getType() || Double.TYPE == field.getType();
    }

    public static Map<RecordField, Set<String>> genMapColumns(Class<?> clazz, Collection<?> records, List<RecordField> recordFieldList) {
        Map<RecordField, Set<String>> mapColumns = new HashMap<>();
        recordFieldList.stream()
                .filter(recordField -> recordField.type() == RecordFieldType.AMOUNT_MAP)
                .forEach(recordField -> {
                    Set<String> keys = records.stream()
                            .map(record -> {
                                try {
                                    Map<String, Object> valueMap = XlsxGeneratorUtils.getValueMap(clazz, recordFieldList, record);
                                    Object obj = XlsxGeneratorUtils.getValueFromMap(clazz, valueMap, recordField);
                                    if (obj != null) {
                                        Map<String, Object> map = (Map<String, Object>) obj;
                                        return map.keySet();
//                                        return map.keySet().stream()
//                                                .map(String::valueOf)
//                                                .collect(Collectors.toSet());
                                    }
                                } catch (IllegalAccessException e) {
                                    e.printStackTrace();
                                }
                                return new HashSet<String>();
                            })
                            .flatMap(Collection::stream)
                            .collect(Collectors.toSet());
                    mapColumns.put(recordField, keys);
                });
        return mapColumns;
    }

}
