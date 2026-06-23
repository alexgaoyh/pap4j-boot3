package cn.net.pap.example.admin.config.validator.dto;

import cn.net.pap.example.admin.config.validator.OrderByEnumValid;
import cn.net.pap.example.admin.config.validator.ValidationDTOValid;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.io.Serializable;

@ValidationDTOValid
@Schema(description = "入参验证数据传输对象")
public class ValidationDTO implements Serializable {

    @Min(value = 1, message = "pageNo大于0")
    @Schema(description = "当前页码", defaultValue = "1", minimum = "1")
    private Integer pageNo = 1;

    @Max(value = 50, message = "pageSize小于50")
    @Min(value = 1, message = "pageSize大于0")
    @Schema(description = "每页大小 (最大50)", defaultValue = "10", minimum = "1", maximum = "50")
    private Integer pageSize = 10;

    @NotEmpty
    @Pattern(regexp = "id|time", message = "只对id或time字段提供排序")
    @Schema(description = "排序字段名称", allowableValues = {"id", "time"})
    private String sortBy;

    @NotEmpty
    // @Pattern(regexp = OrderByEnum.SPLIT, message = "排序方式DESC或ASC")
    @OrderByEnumValid(message = "排序方式应该为DESC或ASC")
    @Schema(description = "排序规则 (ASC升序, DESC降序)", allowableValues = {"ASC", "DESC"})
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
