package top.dtc.settlement.service;

import com.google.common.base.Objects;
import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.data.core.enums.OtcStatus;
import top.dtc.data.core.model.Otc;
import top.dtc.data.core.service.OtcService;
import top.dtc.data.finance.enums.ReferralMode;
import top.dtc.data.finance.service.*;
import top.dtc.settlement.core.properties.NotificationProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Log4j2
@Service
public class CommissionService {

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
        Map<ReferralKey, List<Otc>> referralOtcMap = referralMappingService.list().stream()
                .collect(Collectors.toMap(
                        o -> new ReferralKey(o.referrerId, o.referralMode, o.commissionRate),
                        x -> otcService.getByParams(
                                null,
                                OtcStatus.COMPLETED,
                                Sets.newHashSet(x.clientId),
                                null,
                                date.atStartOfDay(),
                                date.plusDays(1).atStartOfDay()
                        ),
                        (left, right) -> {
                            left.addAll(right);
                            return left;
                        },
                        HashMap::new
                ));
        if (referralOtcMap.isEmpty()) {
            return;
        }
        for (ReferralKey key : referralOtcMap.keySet()) {
            generateOtcCommission(key, referralOtcMap.get(key));
        }
        log.info("Otc Commission Calculated");

    }

    private void generateOtcCommission(ReferralKey key, List<Otc> otcList) {
        // TODO: Create OtcCommission
    }


    @Data
    @AllArgsConstructor
    private static class ReferralKey {
        public Long referrerId;
        public ReferralMode referralMode;
        public BigDecimal commissionRate;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ReferralKey key = (ReferralKey) o;
            return Objects.equal(referrerId, key.referrerId);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(referrerId);
        }
    }

}
