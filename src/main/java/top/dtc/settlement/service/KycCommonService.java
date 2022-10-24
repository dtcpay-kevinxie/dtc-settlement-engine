package top.dtc.settlement.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.common.exception.ValidationException;
import top.dtc.common.util.ClientTypeUtils;
import top.dtc.data.core.enums.ClientStatus;
import top.dtc.data.core.model.Individual;
import top.dtc.data.core.model.NonIndividual;
import top.dtc.data.core.service.IndividualService;
import top.dtc.data.core.service.NonIndividualService;
import top.dtc.data.risk.model.KycIndividual;
import top.dtc.data.risk.service.KycIndividualService;
import top.dtc.data.risk.service.KycNonIndividualService;
import top.dtc.data.risk.service.KycWalletAddressService;

@Log4j2
@Service
public class KycCommonService {

    @Autowired
    NonIndividualService nonIndividualService;

    @Autowired
    IndividualService individualService;

    @Autowired
    KycNonIndividualService kycNonIndividualService;

    @Autowired
    KycIndividualService kycIndividualService;

    @Autowired
    KycWalletAddressService kycWalletAddressService;

    public String getClientName(Long clientId) {
        if (ClientTypeUtils.isIndividual(clientId)) {
            KycIndividual kycIndividual = kycIndividualService.getById(clientId);
            return kycIndividual.lastName + " " + kycIndividual.firstName;
        } else {
            return kycNonIndividualService.getById(clientId).registerName;
        }
    }

    public void validateClientStatus(Long clientId) {
        if (ClientTypeUtils.isIndividual(clientId)) {
            Individual individual = individualService.getById(clientId);
            if (individual == null) {
                throw new ValidationException("Invalid Client ID");
            }
            if (individual.status != ClientStatus.ACTIVATED) {
                throw new ValidationException(String.format("Invalid Client Status [%s]", individual.status.desc));
            }
        } else {
            NonIndividual nonIndividual = nonIndividualService.getById(clientId);
            if (nonIndividual == null) {
                throw new ValidationException("Invalid Client ID");
            }
            if (nonIndividual.status != ClientStatus.ACTIVATED) {
                throw new ValidationException(String.format("Invalid Client Status [%s]", nonIndividual.status.desc));
            }
        }
    }

    public Long getVipLevel(Long clientId) {
        if (ClientTypeUtils.isIndividual(clientId)) {
            return individualService.getById(clientId).vipLevel;
        } else {
            return nonIndividualService.getById(clientId).vipLevel;
        }
    }

}
