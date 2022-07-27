package top.dtc.settlement.handler;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface RecordField {

    int order(); // Starts from 0

    String title() default "";

    String format() default "";

    String fixedValue() default ""; // Will also ignored from excel

    RecordFieldType type() default RecordFieldType.NORMAL;

    String currencyPath() default "";

    String path() default "";

    boolean ignoredFromExcel() default false;

    boolean hidden() default false;

}
