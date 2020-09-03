package top.dtc.settlement.util;

public class CycleIndexUtils {

    private static final long START_TIMESTAMP = 1136044800000L; // 2006-01-01 00:00:00
    private static final int START_YEAR = 2006;
    private static final int START_MONTH = 0;
    private static final int START_DAY = 1;
    private static final long DAILY_MILLIS = 24 * 60 * 60 * 1000;

//    public static int calcCycleIndex(Date transactionTime, int settlementPeriod, String moduleName) {
//        if (settlementPeriod == PeriodConstant.PERIOD_DAILY) {
//            return (int) ((transactionTime.getTime() - START_TIMESTAMP) / DAILY_MILLIS);
//        } else if (settlementPeriod == PeriodConstant.PERIOD_WEEKLY) {
//            long dayDiff = (transactionTime.getTime() - START_TIMESTAMP) / DAILY_MILLIS;
//            return (int) ((dayDiff + (8 - getWeekStartDate(moduleName)) % 7) / 7);
//        } else if (settlementPeriod == PeriodConstant.PERIOD_MONTHLY) {
//            Calendar calendar = DateUtils.toCalendar(transactionTime);
//            return (calendar.get(Calendar.YEAR) - START_YEAR) * 12 + (calendar.get(Calendar.MONTH) - START_MONTH);
//        }
//        return -1;
//    }
//
//    public static Date[] calcCycleDate(int cycleIndex, int settlementPeriod, String moduleName) {
//        if (settlementPeriod == PeriodConstant.PERIOD_DAILY) {
//            Date date = new Date(START_TIMESTAMP + cycleIndex * DAILY_MILLIS);
//            return new Date[] { date, date };
//        } else if (settlementPeriod == PeriodConstant.PERIOD_WEEKLY) {
//            Date startDate = new Date(START_TIMESTAMP + ((getWeekStartDate(moduleName) - 8) % 7 + cycleIndex * 7) * DAILY_MILLIS);
//            Date endDate = new Date(startDate.getTime() + 6 * DAILY_MILLIS);
//            return new Date[] { startDate, endDate };
//        } else if (settlementPeriod == PeriodConstant.PERIOD_MONTHLY) {
//            Date startDate = DateUtils.addMonths(new Date(START_TIMESTAMP), cycleIndex);
//            Date endDate = DateUtils.addDays(DateUtils.addMonths(startDate, 1), -1);
//            return new Date[] { startDate, endDate };
//        }
//        return null;
//    }

//    private static int getWeekStartDate(String hostName){
//        if (WalletType.WECHATPAY.hostName.equals(hostName)){
//            return Calendar.MONDAY;
//        } else {
//            return Calendar.SUNDAY;
//        }
//    }

}
