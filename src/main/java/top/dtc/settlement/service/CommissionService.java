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
import top.dtc.data.core.service.ExchangeRateService;
import top.dtc.data.core.service.OtcService;
import top.dtc.data.finance.enums.CommissionStatus;
import top.dtc.data.finance.enums.ReferralMode;
import top.dtc.data.finance.model.OtcCommission;
import top.dtc.data.finance.service.OtcCommissionService;
import top.dtc.data.finance.service.ReferralMappingService;
import top.dtc.data.finance.service.ReferrerService;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private OtcService otcService;

    @Autowired
    private OtcCommissionService commissionService;

    @Autowired
    private ReferrerService refererService;

    @Autowired
    ExchangeRateService exchangeRateService;

    public void process(LocalDate dateStart, LocalDate dateEnd) {
        Map<ReferralKey, List<Otc>> referralOtcMap = referralMappingService.list().stream()
                .collect(Collectors.toMap(
                        o -> new ReferralKey(o.referrerId, o.referralMode, o.commissionRate),
                        x -> otcService.getByParams(
                                null,
                                OtcStatus.COMPLETED,
                                Sets.newHashSet(x.clientId),
                                null,
                                dateStart.atStartOfDay(),
                                dateEnd.plusDays(1).atStartOfDay()
                        ),
                        (left, right) -> {
                            left.addAll(right);
                            return left;
                        },
                        HashMap::new
                ));
        log.debug("Referral OTC Mappings: {}", referralOtcMap);
        if (referralOtcMap.isEmpty()) {
            return;
        }
        for (ReferralKey key : referralOtcMap.keySet()) {
            generateOtcCommission(key, referralOtcMap.get(key));
        }
        log.info("Otc Commission Calculated");

    }

    private void generateOtcCommission(ReferralKey key, List<Otc> otcList) {
        log.debug("generateOtcCommission: {}", key);
        otcList.forEach(
                otc -> {
                    OtcCommission otcCommission = commissionService.getOneByOtcId(otc.id);
                    if (otcCommission == null) {
                        otcCommission = new OtcCommission();
                    } else if (otcCommission.status != CommissionStatus.PENDING) {
                        log.debug("Skip commission is not in PENDING status {}", otcCommission);
                        return;
                    }
                    BigDecimal profitRate = otc.costRate.subtract(otc.rate).multiply(otc.fiatConvertRate);
                    BigDecimal grossProfit = otc.fiatAmount.multiply(profitRate);
                    otcCommission.referrerId = key.referrerId;
                    otcCommission.status = CommissionStatus.PENDING;
                    otcCommission.otcId = otc.id;
                    otcCommission.otcFiatAmount = otc.fiatAmount;
                    otcCommission.otcCurrency = otc.fiatCurrency;
                    otcCommission.commissionRate = key.commissionRate;
                    otcCommission.commissionCurrency = otc.fiatCurrency;
                    otcCommission.otcTime = otc.completedTime;
                    otcCommission.grossProfitRate = profitRate;
                    if (key.referralMode == ReferralMode.PROFIT_BASE_FIXED) {
                        // PROFIT_BASE_FIXED commission is calculated from gross profit
                        otcCommission.commission = grossProfit.multiply(key.commissionRate).setScale(otcCommission.commissionCurrency.exponent, RoundingMode.DOWN);
                        log.debug("OTC Commission = {} ({} * {}) * {} = {} {}",
                                otcCommission.otcCurrency, otcCommission.otcFiatAmount, profitRate, otcCommission.commissionRate, otcCommission.commissionCurrency, otcCommission.commission);
                    } else if (key.referralMode == ReferralMode.VOLUME_BASE_FIXED) {
                        // VOLUME_BASE_FIXED commission is calculated from OTC amount
                        otcCommission.commission = otcCommission.otcFiatAmount.multiply(otcCommission.commissionRate).setScale(otcCommission.commissionCurrency.exponent, RoundingMode.DOWN);
                        log.debug("OTC Commission = {} {} * {} = {} {}",
                                otcCommission.otcCurrency, otcCommission.otcFiatAmount, otcCommission.commissionRate, otcCommission.commissionCurrency, otcCommission.commission);
                    }
                    commissionService.saveOrUpdate(otcCommission);
                });

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
