package cn.programcx.foxnaserver.dto.ddns;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@Schema(name = "DDNSTaskStatus", description = "DDNS任务状态")
@AllArgsConstructor
public class DDNSTaskStatus {

    @Schema(description = "任务ID", example = "1")
    private Long id; // 任务ID

    @Schema(description = "任务状态", example = "paused")
    private String status;
}
