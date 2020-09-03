package top.dtc.settlement.model.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ApiRequest<T> {

    /**
     * Query attributes object
     */
    T query;

    /**
     * Page attributes if query by page
     */
    Page page;

    public ApiRequest(T query) {
        this.query = query;
    }

    public ApiRequest(T query, Page page) {
        this.query = query;
        this.page = page;
    }

}
