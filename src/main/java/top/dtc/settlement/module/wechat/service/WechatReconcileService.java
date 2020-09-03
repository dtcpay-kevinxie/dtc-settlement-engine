//package top.dtc.settlement.module.wechat.service;
//
//import top.dtc.settlement.constant.Constant;
//import top.dtc.settlement.module.wechat.core.properties.WechatProperties;
//import top.dtc.settlement.module.wechat.model.WechatReconcileData;
//import top.dtc.settlement.service.ReconcileCommonService;
//import top.dtc.settlement.util.PayoutUtils;
//import lombok.extern.log4j.Log4j2;
//import org.apache.commons.lang3.RandomStringUtils;
//import org.apache.commons.lang3.time.DateUtils;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.math.BigDecimal;
//import java.math.RoundingMode;
//import java.nio.file.Files;
//import java.nio.file.Paths;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//
//@Log4j2
//@Service
//@Transactional
//public class WechatReconcileService {
//
//    @Autowired
//    private ReconcileCommonService reconcileCommonService;
//
//    @Autowired
//    private WechatHttpsService wechatHttpsService;
//
//    @Autowired
//    private WechatProperties wechatProperties;
//
//    @Autowired
//    private WechatTransactionService wechatTransactionService;
//
//    @Autowired
//    private WechatAccountInfoService wechatAccountInfoService;
//
//    @Autowired
//    private TransactionService transactionService;
//
//    public boolean executeReconcile(String fetchDate) {
//        String[] currencyPidArr = wechatProperties.appId.split("\\;");
//        log.info("PID number : " + currencyPidArr.length);
//        for (String str : currencyPidArr) {
//            String currency = str.split("\\!")[0];
//            String pid = str.split("\\!")[1];
//            log.info("Loop acquired currency {} and PID {} ", currency, pid);
//            String responseString = null;
//            try {
//                responseString = this.downloadBill(fetchDate, pid, currency);
//            } catch (Exception e) {
//                log.error("download bill data failed", e);
//            }
//            log.info("download bill data : " + responseString);
//            if (!StringUtils.isBlank(responseString) && !"<xml>".equals(responseString.substring(0, 5))) {
//                List<WechatReconcileData> wechatReconcileDataList = this.parseWechatSettlementFile(responseString);
//                processReconcile(wechatReconcileDataList);
//            } else {
//                log.error("bill data invalid.");
//            }
//        }
//        return true;
//    }
//
//    public String downloadBill(String fetchDate, String pid, String currency) throws Exception {
//        String mainUrl = Constant.DOWNLOAD_BILL_API_URL.replace("${WECHAT_NODE}", wechatProperties.wechatMainNode);
//        String spareUrl = Constant.DOWNLOAD_BILL_API_URL.replace("${WECHAT_NODE}", wechatProperties.wechatSpareNode);
//
//        WalletMessage walletMessage = new WalletMessage(TransactionType.DOWNLOAD_BILL);
//        walletMessage.setOfficialAccountId(pid);
//        if (StringUtils.isBlank(fetchDate)) {
//            walletMessage.setBillDate(PayoutUtils.getYesterdayDate());
//        } else {
//            walletMessage.setBillDate(fetchDate);
//        }
//        walletMessage.setBillType(Constant.BILL_TYPE);
//        String mchId = wechatAccountInfoService.getWechatAccountInfo(wechatProperties.mchId, currency);
//        walletMessage.setMerchantId(mchId);
//        // Random strings, not longer than 32 bits
//        walletMessage.setRandomString(RandomStringUtils.randomAlphanumeric(32));
//        // Sign the signature according to the signature rule given by API
//        String postDataXML = PayoutUtils.messageToXml(walletMessage);
//        Map<String, Object> map = PayoutUtils.getMapFromXML(postDataXML);
//        map.put("sign", "");
//        String key = wechatAccountInfoService.getWechatAccountInfo(wechatProperties.key, currency);
//        String sign = PayoutUtils.getSign(map, key);
//        walletMessage.setSign(sign);
//
//        String responseString = wechatHttpsService.sendPost(mainUrl, walletMessage, currency);
//        if ((responseString == null || responseString.equals("")) && !spareUrl.equals("")) {
//            responseString = wechatHttpsService.sendPost(spareUrl, walletMessage, currency);
//        }
//
//        // Store responseString as a local file
//        if (!StringUtils.isBlank(responseString)) {
//            Files.write(Paths.get(wechatProperties.localPath + pid + "_" + currency + "_" + PayoutUtils.getYesterdayDate() + ".txt"), responseString.getBytes());
//        }
//
//        return responseString;
//    }
//
//    public List<WechatReconcileData> parseWechatSettlementFile(String responseString) {
//        List<WechatReconcileData> list = new ArrayList<WechatReconcileData>();
//        WechatReconcileData data = null;
//        responseString = responseString.replaceAll("\\(", "");
//        responseString = responseString.replaceAll("\\)", "");
//        responseString = responseString.replaceAll("\\?", "");
//        responseString = responseString.replaceAll("'", "");
//        String str = "";
//        if (responseString.indexOf("Total transaction count") >= 0) {
//            str = responseString.substring(0, responseString.indexOf("Total transaction count"));
//        }
//        String title = "";
//        String listStr = "";
//        if (str.indexOf("Refund settlement amount") >= 0) {
//            title = str.substring(0, str.indexOf("Refund settlement amount") + 24);
//            listStr = str.substring(str.indexOf("Refund settlement amount") + 24);
//        }
//        title = title.substring(str.indexOf("Transaction time"));
//        title = title.replaceAll(" ", ""); // Go blank
//        String newStr = listStr.replaceAll(",", " ");
//        String[] tempStr = newStr.split("`"); // Data grouping
//        String[] t = title.split(",");// Group header
//        int k = 1; // Record array index
//        int j = tempStr.length / t.length; // Calculate cycle times
//        for (int i = 0; i < j; i++) {
//            data = new WechatReconcileData();
//            for (int l = 0; l < t.length; l++) {
//                data.getAttributeName(t[l].trim(), tempStr[l + k].trim());
//            }
//            k = k + t.length;
//            list.add(data);
//        }
//        return list;
//    }
//
//    private void processReconcile(List<WechatReconcileData> wechatReconcileDataList) {
//        List<PayoutReconcile> reconcileList = new ArrayList<>();
//        for (WechatReconcileData wechatReconcileData : wechatReconcileDataList) {
//            PayoutReconcile reconcile = new PayoutReconcile();
//            buildReconcileAcquirerPart(wechatReconcileData, reconcile);
//            buildReconcileMcpPart(wechatReconcileData, reconcile);
//            reconcileCommonService.processMatching(reconcile);
//            reconcileList.add(reconcile);
//        }
//        reconcileCommonService.insertReconcile(reconcileList);
//    }
//
//    private void buildReconcileAcquirerPart(WechatReconcileData wechatReconcileData, PayoutReconcile reconcile) {
//        long acquirerTransactionAmount = AmountUtil.toCent(wechatReconcileData.totalamounttotal_fee, wechatReconcileData.currencytypefee_type);
//        long acquirerChargeAmount = AmountUtil.toCent(wechatReconcileData.fee, wechatReconcileData.currencytypefee_type);
//        long acquirerNetAmount = acquirerTransactionAmount - acquirerChargeAmount;
//        reconcile.acquirerTransactionId = wechatReconcileData.wechatordernumbertransaction_id;
//        reconcile.acquirerOrderId = wechatReconcileData.vendorordernumberout_transaction_id;
//        reconcile.acquirerType = wechatReconcileData.transactiontypetrade_type;
//        reconcile.acquirerCurrency = wechatReconcileData.currencytypefee_type;
//        reconcile.acquirerAmount = acquirerTransactionAmount;
//        reconcile.acquirerNetAmount = acquirerNetAmount;
//        reconcile.acquirerTransactionTime = DateUtil.getDateAsYYYY_MM_DD_HHMMSS(wechatReconcileData.transactiontime);
//        reconcile.acquirerSettlementTime = DateUtils.addDays(reconcile.acquirerTransactionTime, 1); //WeChat T+1
//        reconcile.moduleName = WalletType.WECHATPAY.hostName;
//        try {
//            reconcile.discountAmount = Long.valueOf(wechatReconcileData.couponamount);
//        } catch (NumberFormatException e) {
//            log.warn("No Coupon Amount");
//            reconcile.discountAmount = 0L;
//        }
//
//    }
//
//    private void buildReconcileMcpPart(WechatReconcileData wechatReconcileData, PayoutReconcile reconcile) {
//        WechatTransaction wechatTransaction = wechatTransactionService.getFirstByOutTradeNo(wechatReconcileData.vendorordernumberout_transaction_id);
//        if (wechatTransaction == null) {
//            log.error("Can't find wechatTransaction by OutTradeNo {}", wechatReconcileData.vendorordernumberout_transaction_id);
//        }
//        Transaction transaction = transactionService.getById(wechatTransaction.id);
//        if (transaction == null) {
//            log.error("Can't find transaction by wechatTransaction.id {}", wechatTransaction.id);
//        }
//        reconcileCommonService.buildReconcileCommonPart(transaction, reconcile);
//        reconcile.settledCurrency = wechatTransaction.currencySettled;
//        reconcile.settledAmount = wechatTransaction.feeInCurrencySettled;
//        BigDecimal merchantNetAmountBigDecimal = new BigDecimal(reconcile.settledAmount)
//                .multiply(Constant.ONE_BIGDECIMAL.subtract(
//                        new BigDecimal(reconcile.acquirerMdr)
//                                .divide(Constant.POWER_6_BIGDECIMAL) // 10 power-6
//                                .divide(Constant.POWER_2_BIGDECIMAL))) // Percentage 10 power-2
//                .setScale(0, RoundingMode.HALF_UP);
//        reconcile.netAmount = merchantNetAmountBigDecimal.longValue();
//        BigDecimal paidAmount = new BigDecimal(reconcile.settledAmount)
//                .multiply(Constant.ONE_BIGDECIMAL.subtract(
//                        new BigDecimal(reconcile.acquirerMdr + reconcile.merchantMdr)
//                                .divide(Constant.POWER_6_BIGDECIMAL) // 10 power-6
//                                .divide(Constant.POWER_2_BIGDECIMAL))) // Percentage 10 power-2
//                .setScale(0, RoundingMode.HALF_UP);
//        reconcile.amountPayMerchant = paidAmount.longValue();
//    }
//
//}
