package top.dtc.settlement.service;

import com.google.common.base.Objects;
import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.common.enums.Currency;
import top.dtc.common.json.JSON;
import top.dtc.common.util.ClientTypeUtils;
import top.dtc.data.core.enums.ClientStatus;
import top.dtc.data.core.enums.OtcStatus;
import top.dtc.data.core.enums.OtcType;
import top.dtc.data.core.model.Individual;
import top.dtc.data.core.model.NonIndividual;
import top.dtc.data.core.model.Otc;
import top.dtc.data.core.service.IndividualService;
import top.dtc.data.core.service.NonIndividualService;
import top.dtc.data.core.service.OtcService;
import top.dtc.data.finance.enums.BonusStatus;
import top.dtc.data.finance.enums.OtcBonusType;
import top.dtc.data.finance.model.OtcBonus;
import top.dtc.data.finance.model.VipScheme;
import top.dtc.data.finance.service.OtcBonusService;
import top.dtc.data.finance.service.OtcReferralProgrammeService;
import top.dtc.data.finance.service.ReferralMappingService;
import top.dtc.data.finance.service.VipSchemeService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@Log4j2
@Service
public class OtcBonusProcessService {

    @Autowired
    OtcBonusService otcBonusService;

    @Autowired
    ReferralMappingService referralMappingService;

    @Autowired
    OtcReferralProgrammeService otcReferralProgrammeService;

    @Autowired
    OtcService otcService;

    @Autowired
    KycCommonService kycCommonService;

    @Autowired
    IndividualService individualService;

    @Autowired
    NonIndividualService nonIndividualService;

    @Autowired
    VipSchemeService vipSchemeService;

    public void processReferralBonus(LocalDate processDate) {
        LocalDate startDate = processDate.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate endDate = processDate.with(TemporalAdjusters.lastDayOfMonth());
        processReferralBonus(startDate, endDate);
    }

    public void processReferralBonus(LocalDate startDate, LocalDate endDate) {
        HashMap<ReferralBonusKey, List<Otc>> referrerOtcSets = referralMappingService.list().stream()
                .collect(Collectors.toMap(
                        o -> {
                            BigDecimal referralBonus = otcReferralProgrammeService.getReferralBonus(
                                    kycCommonService.getVipLevel(o.clientId),
                                    kycCommonService.getVipLevel(o.referrerId)
                            );
                            return new ReferralBonusKey(o.referrerId, o.clientId, referralBonus);
                        },
                        x -> otcService.getByParams(
                                null,
                                OtcStatus.COMPLETED,
                                Sets.newHashSet(x.clientId),
                                null,
                                startDate.atStartOfDay(),
                                endDate.plusDays(1).atStartOfDay()),
                        (left, right) -> {
                            left.addAll(right);
                            return left;
                        },
                        HashMap::new
                ));
        for (ReferralBonusKey referralBonusKey : referrerOtcSets.keySet()) {
            HashMap<Currency, List<Otc>> currencyOtcMaps = groupOtcByCurrency(referrerOtcSets.get(referralBonusKey));
            for (Currency buyCurrency : currencyOtcMaps.keySet()) {
                OtcBonus otcBonus = otcBonusService.getOtcReferralBonus(
                        referralBonusKey.referrerId, referralBonusKey.refereeId, buyCurrency, startDate, endDate
                );
                if (otcBonus == null) {
                    otcBonus= new OtcBonus();
                    otcBonus.type = OtcBonusType.REFERRAL_BONUS;
                    otcBonus.status = BonusStatus.PENDING;
                    otcBonus.clientId = referralBonusKey.referrerId;
                    otcBonus.refereeId = referralBonusKey.refereeId;
                    otcBonus.currency = buyCurrency;
                    otcBonus.createDate = LocalDateTime.now();
                    otcBonus.cycleStartDate = startDate;
                    otcBonus.cycleEndDate = endDate;
                } else if (otcBonus.status != BonusStatus.PENDING) {
                    log.info("OtcBonus has been processed {}", otcBonus);
                    continue;
                }
                otcBonus.amount = currencyOtcMaps.get(buyCurrency).stream()
                        .map(otc -> {
                            if (otc.type == OtcType.BUYING) {
                                return otc.cryptoAmount;
                            } else {
                                return otc.fiatAmount;
                            }
                        })
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .multiply(referralBonusKey.referralBonus);
                otcBonusService.saveOrUpdate(otcBonus);
            }
        }
    }

    public void processAllUserBonus(LocalDate processDate) {
        LocalDate startDate = processDate.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate endDate = processDate.with(TemporalAdjusters.lastDayOfMonth());
        processAllUserBonus(startDate, endDate);
    }

    public void processAllUserBonus(LocalDate startDate, LocalDate endDate) {
        // Get all activated client ids which UserBonus is not 0.00, group by Vip levels
        HashMap<VipScheme, List<Long>> vipSchemeMaps = vipSchemeService.list()
                .stream()
                .filter(vipScheme -> vipScheme.userBonus.compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toMap(
                        o -> o,
                        x -> {
                            List<Long> allIds = new ArrayList<>(
                                    individualService.getByParams(ClientStatus.ACTIVATED, x.id, null, null, null, null)
                                    .stream()
                                    .map(individual -> individual.id)
                                    .toList()
                            );
                            allIds.addAll(
                                    nonIndividualService.getByParams(ClientStatus.ACTIVATED, x.id, null, null, null)
                                    .stream()
                                    .map(nonIndividual -> nonIndividual.id)
                                    .toList()
                            );
                            return allIds;
                        },
                        (left, right) -> {
                            left.addAll(right);
                            return left;
                        },
                        HashMap::new
                ));
        log.info("VipScheme Clients\n{}", JSON.stringify(vipSchemeMaps, true));
        // Calculate UserBonus by Vip levels
        for (VipScheme vipScheme : vipSchemeMaps.keySet()) {
            for (Long clientId : vipSchemeMaps.get(vipScheme)) {
                calculateSingleUserBonus(clientId, vipScheme, startDate, endDate);
            }
        }
    }

    public void calculateAllUserVipLevels(LocalDate startDate, LocalDate endDate) {
        // Get all activated client ids, group by Vip levels
        HashMap<VipScheme, List<Long>> vipSchemeMaps = vipSchemeService.list()
                .stream()
                .collect(Collectors.toMap(
                        o -> o,
                        x -> {
                            List<Long> allIds = new ArrayList<>(
                                    individualService.getByParams(ClientStatus.ACTIVATED, x.id, null, null, null, null)
                                            .stream()
                                            .map(individual -> individual.id)
                                            .toList()
                            );
                            allIds.addAll(
                                    nonIndividualService.getByParams(ClientStatus.ACTIVATED, x.id, null, null, null)
                                            .stream()
                                            .map(nonIndividual -> nonIndividual.id)
                                            .toList()
                            );
                            return allIds;
                        },
                        (left, right) -> {
                            left.addAll(right);
                            return left;
                        },
                        HashMap::new
                ));
        log.info("VipScheme Clients\n{}", JSON.stringify(vipSchemeMaps, true));
        // Calculate Vip by Vip levels
        for (VipScheme vipScheme : vipSchemeMaps.keySet()) {
            for (Long clientId : vipSchemeMaps.get(vipScheme)) {
                calculateSingleUserVipLevel(clientId, vipScheme, startDate, endDate);
            }
        }
    }

    private void calculateSingleUserVipLevel(Long clientId, VipScheme currentVipScheme, LocalDate startDate, LocalDate endDate) {
        List<Otc> otcList = otcService.getByParams(
                null,
                OtcStatus.COMPLETED,
                Sets.newHashSet(clientId),
                null,
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay()
        );
        BigDecimal totalVolumeInUSD = otcList
                .stream()
                .map(otc -> {
                    if (otc.fiatCurrency == Currency.USD) {
                        return otc.fiatAmount;
                    } else {
                        return otc.fiatAmount.divide(otc.fiatConvertRate).setScale(otc.fiatCurrency.exponent, RoundingMode.HALF_UP);
                    }
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalVolumeInUSD.compareTo(currentVipScheme.conditionVolume) <= 0) {
            // Total volume can't fulfill current vip scheme level condition volume, downgrade level
            // VIP 1 conditionVolume is 0, won't have downgrade case
            if (ClientTypeUtils.isIndividual(clientId)) {
                Individual individual = individualService.getById(clientId);
                if (individual.vipLocked != null && !individual.vipLocked) {
                    // Downgrade 1 level
                    individual.vipLevel = individual.vipLevel - 1;
                    individualService.updateById(individual);
                }
            } else {
                NonIndividual nonIndividual = nonIndividualService.getById(clientId);
                if (nonIndividual.vipLocked != null && !nonIndividual.vipLocked) {
                    // Downgrade 1 level
                    nonIndividual.vipLevel = nonIndividual.vipLevel - 1;
                    nonIndividualService.updateById(nonIndividual);
                }
            }
        } else {
            // Total volume greater than current vip scheme level condition volume, upgrade or keep level
            VipScheme nextVipScheme = vipSchemeService.getById(currentVipScheme.id + 1);
            if (nextVipScheme == null) {
                log.info("Already Highest VIP");
                return;
            }
            if (totalVolumeInUSD.compareTo(nextVipScheme.conditionVolume) > 0) {
                // Total volume greater than next level condition volume, Upgrade VIP case
                if (ClientTypeUtils.isIndividual(clientId)) {
                    Individual individual = individualService.getById(clientId);
                    // Upgrade 1 level
                    individual.vipLevel = nextVipScheme.id;
                    individualService.updateById(individual);
                } else {
                    NonIndividual nonIndividual = nonIndividualService.getById(clientId);
                    // Upgrade 1 level
                    nonIndividual.vipLevel = nextVipScheme.id;
                    nonIndividualService.updateById(nonIndividual);
                }
            } else {
                // currentVipScheme.conditionVolume <= accumulatedUSDAmount <= nextVipScheme.conditionVolume
                log.debug("Keep existing Vip Scheme Level");
            }
        }
    }

    private void calculateSingleUserBonus(Long clientId, VipScheme currentVipScheme, LocalDate startDate, LocalDate endDate) {
        List<Otc> otcList = otcService.getByParams(
                null,
                OtcStatus.COMPLETED,
                Sets.newHashSet(clientId),
                null,
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay()
        );
        HashMap<Currency, List<Otc>> currencyOtcMaps = groupOtcByCurrency(otcList);
        for (Currency buyCurrency : currencyOtcMaps.keySet()) {
            OtcBonus otcBonus = otcBonusService.getOtcUserBonus(clientId, buyCurrency, startDate, endDate);
            if (otcBonus == null) {
                otcBonus= new OtcBonus();
                otcBonus.type = OtcBonusType.USER_BONUS;
                otcBonus.status = BonusStatus.PENDING;
                otcBonus.clientId = clientId;
                otcBonus.currency = buyCurrency;
                otcBonus.createDate = LocalDateTime.now();
                otcBonus.cycleStartDate = startDate;
                otcBonus.cycleEndDate = endDate;
            } else if (otcBonus.status != BonusStatus.PENDING) {
                log.info("OtcBonus has been processed {}", otcBonus);
                continue;
            }
            otcBonus.amount = currencyOtcMaps.get(buyCurrency).stream()
                    .map(otc -> {
                        if (otc.type == OtcType.BUYING) {
                            return otc.cryptoAmount;
                        } else {
                            return otc.fiatAmount;
                        }
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .multiply(currentVipScheme.userBonus);
            otcBonusService.saveOrUpdate(otcBonus);
        }
    }

    private HashMap<Currency, List<Otc>> groupOtcByCurrency(List<Otc> otcList) {
        return otcList
                .stream()
                .collect(Collectors.toMap(
                        o -> o.type == OtcType.BUYING ? o.cryptoCurrency : o.fiatCurrency,
                        x -> {
                            List<Otc> list = new ArrayList<>();
                            list.add(x);
                            return list;
                        },
                        (left, right) -> {
                            left.addAll(right);
                            return left;
                        },
                        HashMap::new
                ));
    }

    @AllArgsConstructor
    private static class ReferralBonusKey {
        public Long referrerId;
        public Long refereeId;
        public BigDecimal referralBonus;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ReferralBonusKey key = (ReferralBonusKey) o;
            return Objects.equal(referrerId, key.referrerId)
                    && Objects.equal(refereeId, key.refereeId);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(referrerId, refereeId);
        }
    }



}
