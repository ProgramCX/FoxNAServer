package cn.programcx.foxnaserver.api.monitor;

import cn.programcx.foxnaserver.entity.monitor.SysMainMetrics;
import cn.programcx.foxnaserver.service.monitor.SysMetricsStorageService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/monitor")
@ResponseBody
public class MonitorStatisticsController {

    @Autowired
    private SysMetricsStorageService sysMetricsStorageService;

    @GetMapping("/getByMillRange")
    public ResponseEntity<?> getRecentStatisticsByMillRange(
            @RequestParam("startMills") Long startMills,
            @RequestParam("endMills") Long endMills){
        
        // 参数验证
        if (startMills == null || endMills == null) {
            log.warn("缺少必要参数：startMills 或 endMills");
            return ResponseEntity.badRequest().body("缺少必要参数：startMills 或 endMills");
        }
        
        if (startMills >= endMills) {
            log.warn("时间范围无效：startMills({}) 不能大于等于 endMills({})", startMills, endMills);
            return ResponseEntity.badRequest().body("时间范围无效：开始时间不能大于等于结束时间");
        }
        
        LocalDateTime startTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(startMills),
                ZoneId.systemDefault()
        );

        LocalDateTime endTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(endMills),
                ZoneId.systemDefault()
        );

        try {
            final List<SysMainMetrics> sysMainMetrics = sysMetricsStorageService.selectRecentMetrics(startTime, endTime);
            if (sysMainMetrics == null || sysMainMetrics.isEmpty()) {
                log.warn("查询时间范围 {} 到 {} 内没有监控数据", startTime, endTime);
                return ResponseEntity.ok(sysMainMetrics);
            }
            return ResponseEntity.ok(sysMainMetrics);
        }
        catch (Exception e){
           log.error("获取最近监控数据失败", e);
           return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("获取最近监控数据失败! ");
        }
    }

    @GetMapping("/getRecentStatistics")
    public ResponseEntity<?> getRecentStatistics(@RequestParam("number") int number,
                                                  @RequestParam("unit") String unit) {
        if (number <= 0) {
            log.warn("参数 number 无效：{}", number);
            return ResponseEntity.badRequest().body("参数 number 必须为正整数");
        }
        if (unit == null || unit.trim().isEmpty()) {
            log.warn("缺少必要参数：unit");
            return ResponseEntity.badRequest().body("缺少必要参数：unit");
        }

        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime;

        switch (unit) {
            case "min": {
                startTime = endTime.minusMinutes(number);
                break;
            }
            case "h": {
                startTime = endTime.minusHours(number);
                break;
            }
            case "d": {
                startTime = endTime.minusDays(number);
                break;
            }
            case "m": {
                startTime = endTime.minusMonths(number);
                break;
            }
            case "y": {
                startTime = endTime.minusYears(number);
                break;
            }
            default: {
                log.warn("参数 unit 无效：{}", unit);
                return ResponseEntity.badRequest().body("参数 unit 无效，支持：min/h/d/m/y");
            }
        }

        try {
            final List<SysMainMetrics> sysMainMetrics = sysMetricsStorageService.selectRecentMetrics(startTime, endTime);
            if (sysMainMetrics == null || sysMainMetrics.isEmpty()) {
                log.warn("查询时间范围 {} 到 {} 内没有监控数据", startTime, endTime);
                return ResponseEntity.ok(sysMainMetrics);
            }
            return ResponseEntity.ok(sysMainMetrics);
        } catch (Exception e) {
            log.error("获取最近监控数据失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("获取最近监控数据失败! ");
        }
    }
}
