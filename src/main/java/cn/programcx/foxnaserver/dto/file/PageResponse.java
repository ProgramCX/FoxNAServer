package cn.programcx.foxnaserver.dto.file;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "分页响应数据通用格式")
public class PageResponse<T> {
    @Schema(description = "总记录数")
    private long total;

    @Schema(description = "总页数")
    private int totalPage;

    @Schema(description = "每页大小")
    private int pageSize;

    @Schema(description = "当前页起始记录索引")
    private long from;

    @Schema(description = "当前页结束记录索引")
    private long to;

    @Schema(description = "当前页码")
    private int page;

    @Schema(description = "数据列表")
    private List<T> list;
}
