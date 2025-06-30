package cn.programcx.foxnaserver.dto.file;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "文件或目录信息")
public class FileInfo {
    @Schema(description = "路径")
    private String path;

    @Schema(description = "大小（字节）")
    private long size;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "最后修改时间（时间戳）")
    private long lastModified;

    @Schema(description = "类型，directory 或 file")
    private String type;
}
