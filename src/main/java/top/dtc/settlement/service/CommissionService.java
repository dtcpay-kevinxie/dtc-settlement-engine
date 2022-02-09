package top.dtc.settlement.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.data.core.service.IndividualService;
import top.dtc.data.core.service.OtcService;
import top.dtc.data.finance.service.*;
import top.dtc.settlement.core.properties.NotificationProperties;

import java.time.LocalDate;

@Log4j2
@Service
public class CommissionService {

    @Autowired
    private IndividualService individualService;

    @Autowired
    private ReferralMappingService referralMappingService;

    @Autowired
    private ReferrerService referrerService;

    @Autowired
    private OtcService otcService;

    @Autowired
    private OtcCommissionService commissionService;

    @Autowired
    private PayableService payableService;

    @Autowired
    private PayableSubService payableSubService;

    @Autowired
    NotificationProperties notificationProperties;

    public void process(LocalDate date) {

    }

}
