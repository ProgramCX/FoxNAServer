package cn.programcx.foxnaserver.controller.log;

import cn.programcx.foxnaserver.entity.MongoErrorLog;
import cn.programcx.foxnaserver.service.log.ErrorLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;

@RestController
@RequestMapping("/api/log")
@Tag(name = "Logs", description = "日志查询与管理接口")
public class ErrorLogController {

    @Autowired
    private ErrorLogService errorLogService;

    @GetMapping("/error")
    @Operation(summary = "查询错误日志", description = "支持分页及模块、用户、异常类型、时间范围过滤")
    public ResponseEntity<Page<MongoErrorLog>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String moduleName,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String exceptionType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        Page<MongoErrorLog> result = errorLogService.search(moduleName, userName, exceptionType, startTime, endTime, page, size);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/error/{id}")
    @Operation(summary = "获取错误日志详情")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = MongoErrorLog.class))),
            @ApiResponse(responseCode = "404", description = "未找到日志")
    })
    public ResponseEntity<MongoErrorLog> detail(@PathVariable String id) {
        MongoErrorLog log = errorLogService.findById(id);
        return Optional.ofNullable(log)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @DeleteMapping("/error/before")
    @Operation(summary = "清理历史日志", description = "删除指定时间之前的所有错误日志")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "清理完成"),
            @ApiResponse(responseCode = "400", description = "参数错误")
    })
    public ResponseEntity<String> deleteBefore(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beforeTime) {
        if (beforeTime == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("beforeTime is required");
        }
        errorLogService.deleteOldLogs(beforeTime);
        return ResponseEntity.ok("Logs cleared before " + beforeTime);
    }
}
