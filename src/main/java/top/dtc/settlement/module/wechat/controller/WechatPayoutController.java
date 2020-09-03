//package top.dtc.settlement.module.wechat.controller;
//
//import top.dtc.settlement.constant.Constant;
//import top.dtc.settlement.module.wechat.service.WechatReconcileService;
//import top.dtc.settlement.service.AsyncService;
//import top.dtc.settlement.service.SettlementProcessService;
//import top.dtc.settlement.util.PayoutUtils;
//import lombok.extern.log4j.Log4j2;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Controller;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.ResponseBody;
//
//@Log4j2
//@Controller
//@RequestMapping("/wechat/payout")
//public class WechatPayoutController {
//
//    @Autowired
//    private WechatReconcileService wechatReconcileService;
//
//    @Autowired
//    private AsyncService asyncService;
//
//    @Autowired
//    private SettlementProcessService settlementProcessService;
//
//    @RequestMapping(value = "/reconcile")
//    @ResponseBody
//    public String reconcile(String fetchDate, String groupName, String name, String async) {
//        log.info("/wechat/payout/reconcile");
//        log.info("fetchDate: " + fetchDate + ",groupName: " + groupName + ",name: " + name + ",async: " + async);
//        if (!StringUtils.isBlank(fetchDate)) {
//            fetchDate = fetchDate.replaceAll("-", "");
//        }
//        if ("true".equals(async)) {
//            asyncService.async(fetchDate, groupName, name);
//            log.info("Asynchronous start processing");
//            if (StringUtils.isBlank(fetchDate)) {
//                return PayoutUtils.getYesterdayDate() + ":start";
//            } else {
//                return fetchDate + ":start";
//            }
//        } else {
//            log.info("reimport wechat reconcile.");
//            wechatReconcileService.executeReconcile(fetchDate);
//            boolean flag = settlementProcessService.packReconcileToSettlement(Constant.IS_AUTO_MATCH.TRUE, Constant.MODULE.WECHAT.NAME);
//            if (flag) {
//                log.info("SUCCESS.");
//                return Constant.RESULT.SUCCESS;
//            } else {
//                log.info("FAILED.");
//                return Constant.RESULT.FAILED;
//            }
//        }
//    }
//
//    @RequestMapping("/settlement")
//    @ResponseBody
//    public String settlementTask(String groupName, String name, String async) throws Exception {
//        log.info("settlement start...........................");
//        log.info("groupName: " + groupName + ",name: " + name + ",async: " + async);
//        if ("true".equals(async)) {
//            asyncService.async(null, groupName, name);
//            return PayoutUtils.getYesterdayDate() + ":start";
//        } else {
//            log.info("Calling settlementProcessService.executeSettlement...");
//            boolean flag = settlementProcessService.packReconcileToSettlement(Constant.IS_AUTO_MATCH.TRUE, Constant.MODULE.WECHAT.NAME);
//            if (flag) {
//                return Constant.RESULT.SUCCESS;
//            } else {
//                return Constant.RESULT.FAILED;
//            }
//        }
//    }
//
//}
