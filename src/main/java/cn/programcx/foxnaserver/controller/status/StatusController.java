package cn.programcx.foxnaserver.controller.status;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/status")
@Tag(name = "Status", description = "服务器状态相关接口")
public class StatusController {

    @Operation(
            summary = "获取服务器状态",
            description = "检查服务器是否在线，返回在线状态"
    )
    @ApiResponse(
            responseCode = "200",
            description = "服务器在线",
            content = @io.swagger.v3.oas.annotations.media.Content(
                    mediaType = "text/plain",
                    schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", example = "online")
            )
    )
    @GetMapping("/")
    private ResponseEntity<?> getStatus() {
        log.info("[{}]获取服务器状态成功", System.currentTimeMillis());
        return ResponseEntity.ok("online");
    }
}
