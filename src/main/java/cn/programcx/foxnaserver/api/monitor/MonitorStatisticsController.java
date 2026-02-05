package cn.programcx.foxnaserver.api.monitor;

import cn.programcx.foxnaserver.entity.SysMainMetrics;
import cn.programcx.foxnaserver.service.monitor.SysMetricsStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/monitor")
@Tag(name = "Monitor Statistics", description = "监控统计相关接口")
public class MonitorStatisticsController {

    @Autowired
    private SysMetricsStorageService sysMetricsStorageService;

    @Operation(
            summary = "获取时间范围内的监控统计数据",
            description = "通过毫秒时间戳范围查询监控统计数据，支持 GET 和 POST 请求"
    )
    @ApiResponse(
            responseCode = "200",
            description = "成功返回监控数据列表"
    )
    @ApiResponse(
            responseCode = "400",
            description = "缺少必需的参数 startMills 或 endMills"
    )
    @ApiResponse(
            responseCode = "500",
            description = "服务器内部错误"
    )
    @RequestMapping(value = "/getByMillRange", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<?> getRecentStatisticsByMillRange(
            @RequestParam(value = "startMills", required = false) Long startMills,
            @RequestParam(value = "endMills", required = false) Long endMills,
            @RequestBody(required = false) MetricsRangeRequest payload) {
        
        // Merge parameters from query string and JSON body
        if (payload != null) {
            if (startMills == null) startMills = payload.getStartMills();
            if (endMills == null) endMills = payload.getEndMills();
        }
        
        // Validate required parameters
        if (startMills == null || endMills == null) {
            return ResponseEntity.badRequest()
                .body("startMills and endMills are required via query params or JSON body");
        }
        
        // Convert milliseconds to LocalDateTime
        LocalDateTime startTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(startMills), 
            ZoneId.systemDefault()
        );
        LocalDateTime endTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(endMills), 
            ZoneId.systemDefault()
        );
        
        try {
            List<SysMainMetrics> sysMainMetrics = sysMetricsStorageService.selectRecentMetrics(startTime, endTime);
            return ResponseEntity.ok(sysMainMetrics);
        } catch (Exception e) {
            log.error("获取最近监控数据失败：{}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("获取最近监控数据失败! ");
        }
    }

    @Getter
    public static class MetricsRangeRequest {
        private Long startMills;
        private Long endMills;
        
        public void setStartMills(Long startMills) {
            this.startMills = startMills;
        }
        
        public void setEndMills(Long endMills) {
            this.endMills = endMills;
        }
    }
}
