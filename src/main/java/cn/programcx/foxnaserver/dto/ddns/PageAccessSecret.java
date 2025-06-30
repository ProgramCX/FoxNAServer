package cn.programcx.foxnaserver.dto.ddns;

import cn.programcx.foxnaserver.entity.AccessSecret;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(name = "PageAccessSecret", description = "DDNS访问密钥分页列表")
public class PageAccessSecret {

    @Schema(description = "当前页码", example = "1")
    private long current;

    @Schema(description = "每页数量", example = "10")
    private long size;

    @Schema(description = "总页数", example = "0")
    private long pages;

    @Schema(description = "总记录数", example = "0")
    private long total;

    @Schema(description = "访问密钥列表")
    private List<AccessSecret> records;


    @Schema(description = "排序信息")
    private List<Object> orders;

    @Schema(description = "是否优化count查询", example = "true")
    private boolean optimizeCountSql;

    @Schema(description = "是否进行count查询", example = "true")
    private boolean searchCount;

    @Schema(description = "最大限制", nullable = true)
    private Long maxLimit;

    @Schema(description = "计数ID", nullable = true)
    private String countId;

    // 省略 getter/setter 或用 Lombok @Data
}
