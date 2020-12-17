package top.dtc.settlement.module.etherscan.service;

import com.alibaba.fastjson.JSON;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.common.exception.ValidationException;
import top.dtc.settlement.module.etherscan.core.properties.EtherscanProperties;
import top.dtc.settlement.module.etherscan.model.EtherscanTxnVo;

import java.math.BigDecimal;
import java.math.BigInteger;

@Log4j2
@Service
public class EtherscanService {

    @Autowired
    private EtherscanProperties etherscanProperties;

    public void validateErc20Receivable(BigDecimal amount, String bankAccount, String referenceNo) {
        String url = etherscanProperties.apiUrlPrefix + "/api?"
                + "module=proxy"
                + "&" + "action=eth_getTransactionByHash"
                + "&" + "txhash=" + referenceNo
                + "&" + "apikey=" + etherscanProperties.apiKey;
        HttpResponse<String> respStr = Unirest.post(url).asString();
        log.debug("from etherscan {}", respStr.getBody());
        if (respStr.isSuccess()) {
            EtherscanTxnVo respObject = JSON.parseObject(respStr.getBody(), EtherscanTxnVo.class);
            if (respObject != null && respObject.result != null) {
                String txnInput = respObject.result.input;
//                String methodId = txnInput.substring(0, 10);
                String addressTo = txnInput.substring(10, 74);
                String value = txnInput.substring(74);
                if (!addressTo.contains(bankAccount)) {
                    throw new ValidationException("Transaction Recipient Unmatched");
                }
                if (new BigInteger(value, 16).compareTo(amount.multiply(new BigDecimal(1000000)).toBigInteger()) != 0) {
                    throw new ValidationException("Transaction Amount Unmatched");
                }
                return;
            }
        }
        throw new ValidationException(String.format("Transaction %s not found", referenceNo));
    }
}
