package cn.programcx.foxnaserver.service.monitor;

import cn.programcx.foxnaserver.entity.monitor.SysDiskMetrics;
import cn.programcx.foxnaserver.entity.monitor.SysMainMetrics;
import cn.programcx.foxnaserver.mapper.SysDiskMetricsMapper;
import cn.programcx.foxnaserver.mapper.SysMainMetricsMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Transactional
@Slf4j
@Service
public class SysMetricsStorageService {
    @Autowired
    private SysMainMetricsMapper sysMainMetricsMapper;
    @Autowired
    private SysDiskMetricsMapper sysDiskMetricsMapper;

    public int calculateGroupSeconds(LocalDateTime startTime, LocalDateTime endTime) {
        long seconds = Duration.between(startTime, endTime).getSeconds();

        if (seconds <= 3600) {             // ≤ 1 小时
            return 0;                       // 不分组
        } else if (seconds <= 86400) {      // ≤ 1 天
            return 60;                      // 每 1 分钟
        } else if (seconds <= 7 * 86400) {  // ≤ 7 天
            return 300;                     // 每 5 分钟
        } else if (seconds <= 30 * 86400) { // ≤ 30 天
            return 1800;                    // 每 30 分钟
        } else if (seconds <= 365 * 86400) { // ≤ 1 年
            return 7200;                    // 每 2 小时
        } else {                             // > 1 年
            return 86400;                    // 每天
        }
    }

    public List<SysMainMetrics> selectRecentMetrics(LocalDateTime startTime, LocalDateTime endTime) {
        int groupSeconds = calculateGroupSeconds(startTime, endTime);
        log.info("正在查询时间范围为 {} 到 {} 的系统指标，分组秒数为 {}", startTime, endTime, groupSeconds);
        return sysMainMetricsMapper.selectRecentMetrics(startTime, endTime, groupSeconds);
    }

    public void insertMetrics(SysMainMetrics metrics, List<SysDiskMetrics> diskMetricsList) throws RuntimeException {
        int insertedMetrics = sysMainMetricsMapper.insert(metrics);
        if(insertedMetrics > 0) {
            diskMetricsList.forEach(m -> {
                m.setMainId(metrics.getId());
                sysDiskMetricsMapper.insert(m);
            });
        }else{
            throw new RuntimeException("插入系统指标失败");
        }
    }
}
