package top.dtc.settlement.handler;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Record {

    RecordField[] mappings() default {};

}
