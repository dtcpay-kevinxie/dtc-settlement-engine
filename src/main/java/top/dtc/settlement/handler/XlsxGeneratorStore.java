package top.dtc.settlement.handler;

import com.alibaba.fastjson.JSON;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.springframework.util.ReflectionUtils;
import top.dtc.common.util.StringUtils;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class XlsxGeneratorStore {

    private static final Map<Class<?>, List<RecordField>> recordFieldMap = new HashMap<>();
//    private static final Map<String, SimpleDateFormat> dateFormatMap = new HashMap<>();
    private static final Map<Class<?>, Map<String, List<Field>>> pathFieldMap = new HashMap<>();
    private static final Map<Class<?>, Map<RecordField, String>> recordFieldPathMap = new HashMap<>();
    private static final Map<Enum<?>, String> enumDescMap = new HashMap<>();
    private static final Map<Enum<?>, String> enumNameMap = new HashMap<>();
    private static final Map<Enum<?>, Integer> enumIdMap = new HashMap<>();

    public static synchronized List<RecordField> init(Class<?> clazz) {
        if (recordFieldMap.containsKey(clazz)) {
            return recordFieldMap.get(clazz);
        }
        Record recordAnnotation = clazz.getAnnotation(Record.class);
        if (recordAnnotation == null) {
            throw new UnsupportedOperationException(clazz.getName() + " class without @Record");
        }
        List<RecordField> recordFields = Stream.concat(
                Arrays.stream(clazz.getDeclaredFields())
                        .map(field -> {
                            RecordField recordField = field.getAnnotation(RecordField.class);
                            if (recordField != null) {
                                peekField(clazz, recordField, field.getName(), true);
                                peekField(clazz, recordField, recordField.currencyPath());
                            }
                            return recordField;
                        })
                        .filter(Objects::nonNull),
                Arrays.stream(recordAnnotation.mappings())
                        .peek(recordField -> {
                            peekField(clazz, recordField, recordField.path(), true);
                            peekField(clazz, recordField, recordField.currencyPath());
                        })
        )
                .sorted(Comparator.comparingInt(RecordField::order))
                .collect(Collectors.toList());
        recordFieldMap.put(clazz, recordFields);
        return recordFields;
    }

    public static String getPath(Class<?> clazz, RecordField recordField) {
        return recordFieldPathMap.get(clazz).get(recordField);
    }

    public static Map<String, List<Field>> getPathFieldMap(Class<?> clazz) {
        return pathFieldMap.get(clazz);
    }

//    public static List<Field> getField(Class<?> clazz, String path) {
//        return pathFieldMap.get(clazz).get(path);
//    }
//
//    public static List<Field> getField(Class<?> clazz, RecordField recordField) {
//        return pathFieldMap.get(clazz).get(getPath(clazz, recordField));
//    }

    public static Field getFinalField(Class<?> clazz, RecordField recordField) {
        List<Field> fieldList = pathFieldMap.get(clazz).get(getPath(clazz, recordField));
        if (fieldList == null) {
            throw new NullPointerException(String.format("FieldList is null, %s : %s", clazz.getName(), JSON.toJSONString(recordField)));
        }
        Field field = fieldList.get(fieldList.size() - 1);
        if (field == null) {
            throw new NullPointerException(String.format("Field is null, %s : %s", clazz.getName(), JSON.toJSONString(recordField)));
        }
        return field;
    }

//    public static SimpleDateFormat getDateFormat(String format) {
//        return dateFormatMap.get(format);
//    }

    private static void peekField(Class<?> clazz, RecordField recordField, String path) {
        peekField(clazz, recordField, path, false);
    }

    private static void peekField(Class<?> clazz, RecordField recordField, String path, boolean registerPath) {
        if (StringUtils.isBlank(path)) {
            return;
        }
        String[] pathSegments = path.split("\\.");
        List<Field> fields = new ArrayList<>();
        for (String pathSegment : pathSegments) {
            if (fields.isEmpty()) {
                Field field = ReflectionUtils.findField(clazz, pathSegment);
                if (field == null) {
                    throw new RuntimeException("ProcessorStore: field " + path + " not found");
                }
                fields.add(field);

                if (field.getType().isEnum()) {
                    for (Object enumConstant : field.getType().getEnumConstants()) {
                        getEnumDesc((Enum<?>) enumConstant);
                        getEnumId((Enum<?>) enumConstant);
                        getEnumName((Enum<?>) enumConstant);
                    }
                }
                continue;
            }
            Field field = ReflectionUtils.findField(fields.get(fields.size() - 1).getType(), pathSegment);
            field.setAccessible(true);
            fields.add(field);
        }
        Map<String, List<Field>> fieldMap = pathFieldMap.computeIfAbsent(clazz, k -> new HashMap<>());
        fieldMap.put(path, fields);
        if (registerPath) {
            Map<RecordField, String> pathMap = recordFieldPathMap.computeIfAbsent(clazz, k -> new HashMap<>());
            pathMap.put(recordField, path);
        }
//        String format = recordField.format();
//        if (!StringUtils.isBlank(format) && !dateFormatMap.containsKey(format)) {
//            SimpleDateFormat dateFormat = new SimpleDateFormat(format);
//            dateFormatMap.put(format, dateFormat);
//        }
    }

    public static String getEnumDesc(Enum<?> enumObj) {
        return enumDescMap.computeIfAbsent(enumObj, k -> {
            Field field = FieldUtils.getDeclaredField(enumObj.getClass(), "desc");
            try {
                if (field == null) {
                    return enumObj.name();
                }
                return (String) field.get(enumObj);
            } catch (IllegalAccessException e) {
                return null;
            }
        });
    }

    public static String getEnumName(Enum<?> enumObj) {
        return enumNameMap.computeIfAbsent(enumObj, k -> {
            Field field = FieldUtils.getDeclaredField(enumObj.getClass(), "name");
            try {
                if (field == null) {
                    return enumObj.name();
                }
                return (String) field.get(enumObj);
            } catch (IllegalAccessException e) {
                return null;
            }
        });
    }

    public static Integer getEnumId(Enum<?> enumObj) {
        return enumIdMap.computeIfAbsent(enumObj, k -> {
            Field field = FieldUtils.getDeclaredField(enumObj.getClass(), "id");
            try {
                if (field == null) {
                    return enumObj.ordinal();
                }
                return (Integer) field.get(enumObj);
            } catch (IllegalAccessException e) {
                return null;
            }
        });
    }

}
