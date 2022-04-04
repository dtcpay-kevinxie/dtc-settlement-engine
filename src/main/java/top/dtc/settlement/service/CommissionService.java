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
import top.dtc.data.finance.model.Referrer;
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
        otcList.forEach(
                otc -> {
                    BigDecimal profitRate = otc.rate.subtract(otc.originalRate);
                    BigDecimal grossProfit = otc.fiatAmount.multiply(profitRate);
                    BigDecimal fiatExchangeRate = BigDecimal.ONE;
                    if (grossProfit.compareTo(BigDecimal.ZERO) <= 0) {
                        return;
                    }
                    OtcCommission otcCommission = new OtcCommission();
                    otcCommission.referrerId = key.referrerId;
                    otcCommission.status = CommissionStatus.PENDING;
                    otcCommission.otcId = otc.id;
                    otcCommission.otcFiatAmount = otc.fiatAmount;
                    otcCommission.otcCurrency = otc.fiatCurrency;
                    otcCommission.commissionRate = key.commissionRate;
                    Referrer referrer = refererService.getById(key.referrerId);
                    otcCommission.commissionCurrency = referrer.settleCurrency;
                    otcCommission.otcTime = otc.completedTime;
                    if (otcCommission.commissionCurrency != otcCommission.otcCurrency) {
                        fiatExchangeRate = exchangeRateService.getMiddleRate(otc.fiatCurrency, referrer.settleCurrency);
                    }
                    if (key.referralMode == ReferralMode.PROFIT_BASE_FIXED) {
                        // PROFIT_BASE_FIXED commission is calculated from gross profit
                        otcCommission.commission = grossProfit.multiply(key.commissionRate).multiply(fiatExchangeRate).setScale(otcCommission.commissionCurrency.exponent, RoundingMode.DOWN);
                        log.debug("OTC Commission = {} ({} * {}) * {} * {} = {} {}",
                                otcCommission.otcCurrency, otcCommission.otcFiatAmount, profitRate, otcCommission.commissionRate, fiatExchangeRate, otcCommission.commissionCurrency, otcCommission.commission);
                    } else if (key.referralMode == ReferralMode.VOLUME_BASE_FIXED) {
                        // VOLUME_BASE_FIXED commission is calculated from OTC amount
                        if (otcCommission.commissionRate.compareTo(profitRate) > 0) {
                            otcCommission.commission = otcCommission.otcFiatAmount.multiply(otcCommission.commissionRate).setScale(otcCommission.commissionCurrency.exponent, RoundingMode.DOWN);
                            log.debug("OTC Commission = {} {} * {} * {} = {} {}",
                                    otcCommission.otcCurrency, otcCommission.otcFiatAmount, otcCommission.commissionRate, fiatExchangeRate, otcCommission.commissionCurrency, otcCommission.commission);
                        } else {
                            // profit rate is lower than commission rate
                            return;
                        }
                    }
                    commissionService.save(otcCommission);
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
