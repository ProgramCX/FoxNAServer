package cn.programcx.foxnaserver.service.monitor;

import cn.programcx.foxnaserver.entity.SysMainMetrics;
import cn.programcx.foxnaserver.mapper.SysMainMetricsMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class SysMetricsStorageService {

    @Autowired
    private SysMainMetricsMapper sysMainMetricsMapper;

    /**
     * Query recent metrics within a time range
     * @param startTime Start time
     * @param endTime End time
     * @return List of metrics
     */
    public List<SysMainMetrics> selectRecentMetrics(LocalDateTime startTime, LocalDateTime endTime) {
        int groupSeconds = calculateGroupSeconds(startTime, endTime);
        log.info("正在查询时间范围为 {} 到 {} 的系统指标，分组秒数为 {}", startTime, endTime, groupSeconds);
        return sysMainMetricsMapper.selectRecentMetrics(startTime, endTime, groupSeconds);
    }

    /**
     * Calculate appropriate grouping interval based on time range
     * @param startTime Start time
     * @param endTime End time
     * @return Grouping interval in seconds
     */
    private int calculateGroupSeconds(LocalDateTime startTime, LocalDateTime endTime) {
        Duration duration = Duration.between(startTime, endTime);
        long hours = duration.toHours();
        
        if (hours <= 1) {
            // For 1 hour or less, group by 60 seconds (1 minute)
            return 60;
        } else if (hours <= 6) {
            // For up to 6 hours, group by 300 seconds (5 minutes)
            return 300;
        } else if (hours <= 24) {
            // For up to 24 hours, group by 600 seconds (10 minutes)
            return 600;
        } else if (hours <= 168) {
            // For up to 7 days, group by 3600 seconds (1 hour)
            return 3600;
        } else {
            // For more than 7 days, group by 86400 seconds (1 day)
            return 86400;
        }
    }
}
