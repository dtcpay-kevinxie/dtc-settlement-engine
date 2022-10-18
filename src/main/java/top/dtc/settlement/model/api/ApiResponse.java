package top.dtc.settlement.model.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
public class ApiResponse<T> {

    public ApiHeader header;

    public T result;

    public List<T> resultList;

    public IPage<T> resultPage;

    public ApiResponse(ApiHeader header) {
        this.header = header;
    }

    public ApiResponse(ApiHeader header, T result) {
        this.header = header;
        this.result = result;
    }

    public ApiResponse(ApiHeader header, List<T> resultList) {
        this.header = header;
        this.resultList = resultList;
    }

    public ApiResponse(ApiHeader header, IPage<T> resultPage) {
        this.header = header;
        this.resultPage = resultPage;
    }

}
