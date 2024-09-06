package cn.net.pap.example.admin.config.validator.dto;

import cn.net.pap.example.admin.config.validator.OrderByEnumValid;
import cn.net.pap.example.admin.config.validator.ValidationDTOValid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.io.Serializable;

@ValidationDTOValid
public class ValidationDTO implements Serializable {

    @Min(value = 1, message = "pageNo大于0")
    private Integer pageNo = 1;

    @Max(value = 50, message = "pageSize小于50")
    @Min(value = 1, message = "pageSize大于0")
    private Integer pageSize = 10;

    @NotEmpty
    @Pattern(regexp = "id|time", message = "只对id或time字段提供排序")
    private String sortBy;

    @NotEmpty
    // @Pattern(regexp = OrderByEnum.SPLIT, message = "排序方式DESC或ASC")
    @OrderByEnumValid(message = "排序方式应该为DESC或ASC")
    private String order;

    public @Min(value = 1, message = "pageNo大于0") Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(@Min(value = 1, message = "pageNo大于0") Integer pageNo) {
        this.pageNo = pageNo;
    }

    public @Max(value = 50, message = "pageSize小于50") @Min(value = 1, message = "pageSize大于0") Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(@Max(value = 50, message = "pageSize小于50") @Min(value = 1, message = "pageSize大于0") Integer pageSize) {
        this.pageSize = pageSize;
    }

    public @NotEmpty @Pattern(regexp = "id|time", message = "只对id或time字段提供排序") String getSortBy() {
        return sortBy;
    }

    public void setSortBy(@NotEmpty @Pattern(regexp = "id|time", message = "只对id或time字段提供排序") String sortBy) {
        this.sortBy = sortBy;
    }

    public @NotEmpty String getOrder() {
        return order;
    }

    public void setOrder(@NotEmpty String order) {
        this.order = order;
    }
}
