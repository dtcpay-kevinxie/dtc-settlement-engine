package top.dtc.settlement.util;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.digest.DigestUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.*;

@Log4j2
public class PayoutUtils {

    public static InputStream getStringStream(String sInputString) {
        ByteArrayInputStream tInputStringStream = null;
        if (sInputString != null && !sInputString.trim().equals("")) {
            tInputStringStream = new ByteArrayInputStream(sInputString.getBytes());
        }
        return tInputStringStream;
    }

    /**
     * Add one day to the current date
     *
     * @return
     */
//    public static String getYesterdayDate() {
//        return DateUtil.getDateAsYYYYMMDD(DateUtils.addDays(new Date(), -1));
//    }

    /**
     * The array of all elements in accordance with the "parameter = parameter value" model,
     * Join the string with the "&" character
     *
     * @param params  A parameter group that requires sorting and joining characters
     * @param charset Coded format
     * @return Spliced string
     */
    public static String createLinkStringUrlEncode(Map<String, Object> params, Charset charset) {

        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);

        StringBuilder prestr = new StringBuilder();

        for (int i = 0; i < keys.size(); i++) {
            String key = (String) keys.get(i);
            String value = (String) params.get(key);

            try {
                if (i == keys.size() - 1) {
                    prestr.append(key).append("=").append(value == null ? "" : URLEncoder.encode(value, charset.name()));
                } else {
                    prestr.append(key).append("=").append(value == null ? "" : URLEncoder.encode(value, charset.name())).append("&");
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                log.error("Create link string error", e);
            }
        }

        return prestr.toString();
    }

    public static Map<String, Object> getMapFromXML(String xmlString)
            throws ParserConfigurationException, IOException, SAXException {

        //Here, the main purpose of parsing packages back in Dom is to prevent API from adding new wrapped fields
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        InputStream is = PayoutUtils.getStringStream(xmlString);
        Document document = builder.parse(is);

        //Get all the nodes inside the document
        NodeList allNodes = document.getFirstChild().getChildNodes();
        Node node;
        Map<String, Object> map = new HashMap<>();
        int i = 0;
        while (i < allNodes.getLength()) {
            node = allNodes.item(i);
            if (node instanceof Element) {
                map.put(node.getNodeName(), node.getTextContent());
            }
            i++;
        }
        return map;

    }

    public static String getSign(Map<String, Object> map, String key) {
        ArrayList<String> list = new ArrayList<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() != "") {
                list.add(entry.getKey() + "=" + entry.getValue() + "&");
            }
        }
        int size = list.size();
        String[] arrayToSort = list.toArray(new String[size]);
        Arrays.sort(arrayToSort, String.CASE_INSENSITIVE_ORDER);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            sb.append(arrayToSort[i]);
        }
        String result = sb.toString();
        result += "key=" + key;
        result = DigestUtils.md5Hex(result).toUpperCase();
        return result;
    }

//    /**
//     * Converting entities to XML
//     *
//     * @param message
//     * @return
//     */
//    public static String messageToXml(WalletMessage message) {
//        String xml = genXStream(message).toXML(message);
//        xml = xml.replaceAll("__", "_");
//        log.info(xml);
//        return xml;
//    }
//
//    private static XStream genXStream(WalletMessage message) {
//        XStream xStream = new XStream(new DomDriver("utf8"));
//        xStream.alias("WalletMessage", WalletMessage.class);
//        xStream.omitField(Message.class, "type");
//        xStream.omitField(Message.class, "serverData");
//        xStream.omitField(Message.class, "securityData");
//        xStream.omitField(WalletMessage.class, "walletPaymentType");
//        xStream.omitField(Message.class, "data");
//        //request
//        xStream.aliasField("bill_date", WalletMessage.class, "billDate");
//        xStream.aliasField("bill_type", WalletMessage.class, "billType");
//        xStream.aliasField("body", WalletMessage.class, "body");
//        xStream.aliasField("spbill_create_ip", WalletMessage.class, "ipAddress");
//        xStream.aliasField("time_start", WalletMessage.class, "transactionStartTime");
//        xStream.aliasField("time_expire", WalletMessage.class, "transactionEndTime");
//        xStream.aliasField("goods_tag", WalletMessage.class, "itemsLabel");
//        xStream.aliasField("auth_code", WalletMessage.class, "authCode");
//        xStream.aliasField("product_id", WalletMessage.class, "productId");
//        xStream.aliasField("refund_fee_type", WalletMessage.class, "refundCurrency");
//        xStream.aliasField("op_user_id", WalletMessage.class, "operatorId");
//        xStream.aliasField("interface_url", WalletMessage.class, "interfaceUrl");
//        //        xStream.aliasField("execute_time_cost", WalletMessage.class, "");
//        xStream.aliasField("user_ip", WalletMessage.class, "interfaceIp");
//        xStream.aliasField("time", WalletMessage.class, "merchantReportingTime");
//        xStream.aliasField("offset", WalletMessage.class, "offset");
//        xStream.aliasField("usetag", WalletMessage.class, "settlementStatus");
//        xStream.aliasField("limit", WalletMessage.class, "maximumNumber");
//        xStream.aliasField("date_start", WalletMessage.class, "startDate");
//        xStream.aliasField("date_end", WalletMessage.class, "endDate");
//        xStream.aliasField("date", WalletMessage.class, "clientRequestDate");
//        xStream.aliasField("tar_type", WalletMessage.class, "CompressionBill");
//        //response
//        xStream.aliasField("return_code", WalletMessage.class, "respCode");
//        xStream.aliasField("return_msg", WalletMessage.class, "hostResponseMessage");
//        xStream.aliasField("trade_state", WalletMessage.class, "transactionState");
//        xStream.aliasField("openid", WalletMessage.class, "userTag");
//        xStream.aliasField("is_subscribe", WalletMessage.class, "isFollowingOfficialAcc");
//        xStream.aliasField("trade_type", WalletMessage.class, "walletType");
//        xStream.aliasField("bank_type", WalletMessage.class, "bankType");
//        xStream.aliasField("total_fee", WalletMessage.class, "totalAmount");
//        //        xStream.aliasField("coupon_fee", WalletMessage.class, "");
//        xStream.aliasField("fee_type", WalletMessage.class, "currency");
//        xStream.aliasField("attach", WalletMessage.class, "additionalText");
//        xStream.aliasField("time_end", WalletMessage.class, "paymentEndTime");
//        xStream.aliasField("sub_mch_id", WalletMessage.class, "subMerchantId");
//        xStream.aliasField("device_info", WalletMessage.class, "deviceId");
//        xStream.aliasField("transaction_id", WalletMessage.class, "receiptNumber");
//        xStream.aliasField("out_trade_no", WalletMessage.class, "merchantOrderNo");
//        xStream.aliasField("refund_count", WalletMessage.class, "refundCount");
//        xStream.aliasField("notify_url", WalletMessage.class, "notificationUrl");
//        xStream.aliasField("out_refund_no", WalletMessage.class, "merchantRefundNo");
//        xStream.aliasField("refund_id", WalletMessage.class, "wechatRefundNo");
//        xStream.aliasField("refund_channel", WalletMessage.class, "refundChannel");
//        xStream.aliasField("refund_fee", WalletMessage.class, "refundAmount");
//        xStream.aliasField("cash_refund_fee", WalletMessage.class, "cashRefundAmount");
//        xStream.aliasField("cash_refund_fee_type", WalletMessage.class, "cashRefundCurrency");
//        xStream.aliasField("refund_status", WalletMessage.class, "refundState");
//        xStream.aliasField("result_code", WalletMessage.class, "resultCode");
//        xStream.aliasField("err_code", WalletMessage.class, "errorCode");
//        xStream.aliasField("err_code_des", WalletMessage.class, "errorCodeDesc");
//        xStream.aliasField("sign", WalletMessage.class, "sign");
//        xStream.aliasField("sign_type", WalletMessage.class, "signType");
//        xStream.aliasField("appid", WalletMessage.class, "officialAccountId");
//        xStream.aliasField("mch_id", WalletMessage.class, "merchantId");
//        xStream.aliasField("nonce_str", WalletMessage.class, "randomString");
//        xStream.aliasField("rate", WalletMessage.class, "currencyRate");
//        xStream.aliasField("rate_time", WalletMessage.class, "rateTime");
//        xStream.aliasField("cash_fee_type", WalletMessage.class, "payerCurrency");
//        xStream.aliasField("cash_fee", WalletMessage.class, "payerAmount");
//        xStream.aliasField("recall", WalletMessage.class, "recall");
//        xStream.aliasField("sub_appid", WalletMessage.class, "subOfficialAccountId");
//
//        xStream.aliasField("detail", WalletMessage.class, "itemsDetails");
//        xStream.aliasField("trade_type", WalletMessage.class, "tradeType");
//        xStream.aliasField("responseXml", WalletMessage.class, "responseXml");
//
//        return xStream;
//    }

}

