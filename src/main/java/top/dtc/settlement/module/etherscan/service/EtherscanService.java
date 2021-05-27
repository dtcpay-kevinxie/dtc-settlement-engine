package top.dtc.settlement.module.etherscan.service;

import com.alibaba.fastjson.JSON;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.common.exception.ValidationException;
import top.dtc.common.util.StringUtils;
import top.dtc.data.risk.model.KycWalletAddress;
import top.dtc.settlement.module.etherscan.core.properties.EtherscanProperties;
import top.dtc.settlement.module.etherscan.model.EtherscanErc20Event;
import top.dtc.settlement.module.etherscan.model.EtherscanTokenTx;
import top.dtc.settlement.module.etherscan.model.EtherscanTxnByHash;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

@Log4j2
@Service
public class EtherscanService {

    @Autowired
    private EtherscanProperties etherscanProperties;

    public void validateErc20Txn(BigDecimal amount, String recipientAddress, String referenceNo) {
        String url = etherscanProperties.apiUrlPrefix + "/api?"
                + "module=proxy"
                + "&" + "action=eth_getTransactionByHash"
                + "&" + "txhash=" + referenceNo
                + "&" + "apikey=" + etherscanProperties.apiKey;
        HttpResponse<String> respStr = Unirest.post(url).asString();
        log.debug("from {}\n {}", url, respStr.getBody());
        if (respStr.isSuccess()) {
            EtherscanTxnByHash respObject = JSON.parseObject(respStr.getBody(), EtherscanTxnByHash.class);
            if (respObject != null && respObject.result != null) {
                String txnInput = respObject.result.input;
                if (txnInput.equals("0x")) {
                    // ETH transaction
                    String addressTo = respObject.result.to;
                    String value = respObject.result.value.substring(2);
                    log.debug("addressTo={} \n value={}", addressTo, value);
                    if (!addressTo.contains(recipientAddress.toLowerCase())) { // remove "0x" from address
                        throw new ValidationException("Transaction Recipient Unmatched");
                    }
                    if (new BigInteger(value, 16).compareTo(amount.multiply(BigDecimal.TEN.pow(18)).toBigInteger()) < 0) {
                        throw new ValidationException("Amount from blockchain is smaller than write-off amount.");
                    }
                } else {
                    // Other token (USDT, etc.) smart contract transaction in Ethereum
                    String methodId = txnInput.substring(0, 10);
                    String addressTo = txnInput.substring(10, 74).toLowerCase();
                    String value = txnInput.substring(74);
                    log.debug("methodId={} \n addressTo={} \n value={}", methodId, addressTo, value);
                    if (!addressTo.contains(recipientAddress.substring(2).toLowerCase())) { // remove "0x" from address
                        throw new ValidationException("Transaction Recipient Unmatched");
                    }
                    if (new BigInteger(value, 16).compareTo(amount.multiply(BigDecimal.TEN.pow(6)).toBigInteger()) < 0) {
                        throw new ValidationException("Amount from blockchain is smaller than write-off amount.");
                    }
                }
                return;
            }
        }
        throw new ValidationException(String.format("Transaction %s not found", referenceNo));
    }

    public List<EtherscanErc20Event> checkNewTransactions(KycWalletAddress dtcOpsAddress) {
        String startBlock;
        if (StringUtils.isBlank(dtcOpsAddress.lastTxnBlock)) {
            startBlock = "0";
        } else {
            startBlock = new BigInteger(dtcOpsAddress.lastTxnBlock).add(BigInteger.ONE).toString();
        }
        String url = etherscanProperties.apiUrlPrefix + "/api?"
                + "module=account"
                + "&" + "action=tokenTx"
                + "&" + "address=" + dtcOpsAddress.address
                + "&" + "startblock=" + startBlock
                + "&" + "endblock=" + etherscanProperties.maxEndBlock
                + "&" + "sort=asc"
                + "&" + "apikey=" + etherscanProperties.apiKey;
        HttpResponse<String> respStr = Unirest.post(url).asString();
        log.debug("from {}\n {}", url, respStr.getBody());
        if (respStr.isSuccess()) {
            EtherscanTokenTx respObject = JSON.parseObject(respStr.getBody(), EtherscanTokenTx.class);
            if (respObject != null
                    && "1".equals(respObject.status)
                    && respObject.result != null
                    && respObject.result.size() > 0
            ) {
                return respObject.result;
            }
        }
        return null;
    }

}
