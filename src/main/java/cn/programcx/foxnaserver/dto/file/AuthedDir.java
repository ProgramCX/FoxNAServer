package cn.programcx.foxnaserver.dto.file;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "授权目录信息")
public class AuthedDir {

    @Schema(description = "目录路径", example = "C:/Users/")
    private String path;

    @Schema(description = "权限类型（多个以逗号分隔）", example = "Read,Write")
    private String permissions;
}
