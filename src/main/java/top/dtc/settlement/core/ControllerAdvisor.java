package top.dtc.settlement.core;

import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Log4j2
@ControllerAdvice
public class ControllerAdvisor {

    @ExceptionHandler
    public void allExceptions(Exception e) {
        log.error("ControllerAdvisor", e);
    }

}
