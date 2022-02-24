package top.dtc.settlement.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.common.util.ClientTypeUtils;
import top.dtc.data.risk.model.KycIndividual;
import top.dtc.data.risk.service.KycIndividualService;
import top.dtc.data.risk.service.KycNonIndividualService;
import top.dtc.data.wallet.enums.UserStatus;
import top.dtc.data.wallet.model.WalletUser;
import top.dtc.data.wallet.service.WalletUserService;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Log4j2
@Service
public class CommonValidationService {

    @Autowired
    KycNonIndividualService kycNonIndividualService;

    @Autowired
    KycIndividualService kycIndividualService;

    @Autowired
    WalletUserService walletUserService;

    public String getClientName(Long clientId) {
        if (ClientTypeUtils.isIndividual(clientId)) {
            KycIndividual kycIndividual = kycIndividualService.getById(clientId);
            return kycIndividual.lastName + " " + kycIndividual.firstName;
        } else {
            return kycNonIndividualService.getById(clientId).registerName;
        }
    }

    public String getClientEmail(Long clientId) {
        if (ClientTypeUtils.isIndividual(clientId)) {
            KycIndividual kycIndividual = kycIndividualService.getById(clientId);
            return kycIndividual.email;
        } else {
            return kycNonIndividualService.getById(clientId).email;
        }
    }

    public List<String> getClientUserEmails(Long clientId) {
        List<WalletUser> walletUserList = walletUserService.getByClientIdAndStatus(clientId, UserStatus.ENABLED);
        if (walletUserList == null || walletUserList.isEmpty()) {
            log.info("Not Wallet user for client");
            return new ArrayList<>();
        } else {
            return walletUserList.stream().map(walletUser -> walletUser.email).collect(Collectors.toList());
        }
    }

}
