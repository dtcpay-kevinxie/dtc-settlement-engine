package top.dtc.settlement.module.wechat.model;

import lombok.Data;
import lombok.extern.log4j.Log4j2;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by luo ting on 2017/11/09.
 */
@Log4j2
@Data
public class WechatReconcileData {

    public String transactiontime;
    public String officialaccountIDappid;
    public String vendorIDmch_id;
    public String subvendorIDsub_mch_id;
    public String deviceIDDevice_info;
    public String wechatordernumbertransaction_id;
    public String vendorordernumberout_transaction_id;
    public String usertagopenid;
    public String transactiontypetrade_type;
    public String transactionstatustrade_state;
    public String paymentbankbank_type;
    public String currencytypefee_type;
    public String totalamounttotal_fee;
    public String couponamount;
    public String wechatrefundnumberrefund_id;
    public String vendorrefundnumberout_refund_no;
    public String refundamountrefund_fee;
    public String couponrefundamount;
    public String refundtype;
    public String refundstatusrefund_status_$n;
    public String productname;
    public String vendorsdatapackageattach;
    public String fee;
    public String rate;
    public String paymentCurrencytypeCash_fee_type;
    public String cashpaymentamountCash_fee;
    public String settlementcurrencytype;
    public String settlementcurrencyamount;
    public String exchangerate;
    public String refundexchangerate;
    public String payersRefundamount;
    public String payersRefundcurrencytype;
    public String refundcurrencytype;
    public String refundsettlementcurrencytype;
    public String refundsettlementamount;

    public void getAttributeName(String key, String value) {
        Field[] fields = this.getClass().getDeclaredFields();
        for (Field field : fields) {
            String name = field.getName();
            if (name.equalsIgnoreCase(key)) {
                name = name.substring(0, 1).toUpperCase() + name.substring(1);
                try {
                    Method m = this.getClass().getMethod("get" + name);
                    String val = (String) m.invoke(this);
                    if (val == null) {
                        m = this.getClass().getMethod("set" + name, String.class);
                        m.invoke(this, value == null ? "" : value);
                    }
                } catch (NoSuchMethodException e) {
                    e.printStackTrace();
                    log.error("Wechat Transaction Data assignment error(NoSuchMethodException)", e);
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                    log.error("Wechat Transaction Data assignment error(InvocationTargetException)", e);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    log.error("Wechat Transaction Data assignment error(IllegalAccessException)", e);
                }
            }
        }
    }

}
