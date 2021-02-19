package top.dtc.settlement.module.silvergate.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.settlement.module.silvergate.core.properties.SilvergateProperties;

@Log4j2
@Service
public class SilvergateApiService {

    @Autowired
    private SilvergateProperties silvergateProperties;

}
