package top.dtc.settlement.report_processor.vo;

import com.google.common.base.Objects;
import lombok.Data;
import top.dtc.common.enums.Currency;

import java.math.BigDecimal;

@Data
public class TotalSortingObject {

    public Long clientId;
    public String clientName;
    public String country;
    public Currency currency;
    public BigDecimal totalAmount;
    public long totalCount;

    public TotalSortingObject(Long clientId, String country, Currency currency) {
        this.clientId = clientId;
        this.country = country;
        this.currency = currency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TotalSortingObject key = (TotalSortingObject) o;
        return Objects.equal(currency, key.currency)
                && Objects.equal(clientId, key.clientId)
                && Objects.equal(country, key.country);
    }

    @Override
    public int hashCode() { return Objects.hashCode(currency, country, clientId);}

}
