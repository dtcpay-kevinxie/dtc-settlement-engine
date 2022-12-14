package top.dtc.settlement.report_processor.vo;

import top.dtc.addon.data_processor.DataRecord;
import top.dtc.addon.data_processor.RecordField;
import top.dtc.data.wallet.model.WalletBalanceHistory;

import java.math.BigDecimal;

import static top.dtc.addon.data_processor.RecordFieldType.ENUM_NAME;

@DataRecord(mappings = {
        @RecordField(order = 0, title = "Client ID", path = "clientId"),
        @RecordField(order = 1, title = "Wallet Account ID", path = "walletAccountId"),
        @RecordField(order = 2, title = "Currency", path = "currency", type = ENUM_NAME),
        @RecordField(order = 3, title = "Balance Before", path = "balanceBefore"),
        @RecordField(order = 4, title = "Balance After", path = "balanceAfter"),
        @RecordField(order = 5, title = "Change Amount", path = "changeAmount"),
        @RecordField(order = 6, title = "Activity", path = "type", type = ENUM_NAME),
        @RecordField(order = 7, title = "Related ID", path = "relatedId")
})
public class WalletBalanceChangeHistoryReport extends WalletBalanceHistory {

    @RecordField(order = 8, title = "Rate to SGD")
    public BigDecimal rateToSGD;

    @RecordField(order = 9, title = "Flow Direction")
    public String flowDirection;

}
