package top.dtc.settlement.handler;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface RecordField {

    int order();

    String format() default "";

    String fixedValue() default ""; // Will also ignored from excel

    boolean ignoredFromExcel() default false;

}
