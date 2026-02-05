package cn.programcx.foxnaserver.jobs;

import cn.programcx.foxnaserver.entity.monitor.SysDiskMetrics;
import cn.programcx.foxnaserver.entity.monitor.SysMainMetrics;
import cn.programcx.foxnaserver.service.monitor.SysMetricsStorageService;
import cn.programcx.foxnaserver.service.status.StatusService;
import kotlin.Pair;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class SysMetricsScheduler {
    @Autowired
    private SysMetricsStorageService sysMetricsStorageService;

    @Autowired
    private StatusService statusService;


    private Pair<SysMainMetrics, List<SysDiskMetrics>> collectMetrics() {
        statusService.iniStatistics();
        final double cpuUsage = statusService.getCPUUsage();
        final Map<String, Object> memoryInfo = statusService.getMemoryInfo();
        final List<Map<String, Object>> diskInfo = statusService.getDiskInfo();
        final List<Map<String, Object>> networkInfo = statusService.getNetworkInfo();

        Long totalMemory = ((Number) memoryInfo.get("total")).longValue();
        Long usedMemory = ((Number) memoryInfo.get("used")).longValue();


        Long downloadSpeed = 0L;
        Long uploadSpeed = 0L;

        List<SysDiskMetrics> sysDiskMetricsList = new ArrayList<>();
        diskInfo.forEach(disk -> {
            SysDiskMetrics sysDiskMetrics = new SysDiskMetrics();
            sysDiskMetrics.setDiskName(disk.get("name").toString());
            sysDiskMetrics.setTotalSpace(((Number) disk.get("total")).longValue());
            sysDiskMetrics.setUsedSpace(((Number) disk.get("used")).longValue());
            sysDiskMetricsList.add(sysDiskMetrics);
        });

        for (Map<String, Object> network : networkInfo) {
            downloadSpeed += ((Number) network.get("recvSpeed")).longValue();
            uploadSpeed += ((Number) network.get("sentSpeed")).longValue();
        }

        SysMainMetrics sysMainMetrics = new SysMainMetrics();
        sysMainMetrics.setCpu(cpuUsage);
        sysMainMetrics.setTotalMemory(totalMemory);
        sysMainMetrics.setUsedMemory(usedMemory);
        sysMainMetrics.setDownloadSpeed(downloadSpeed);
        sysMainMetrics.setUploadSpeed(uploadSpeed);

        return new Pair<>(sysMainMetrics, sysDiskMetricsList);
    }

        @Scheduled(cron = "0 0/1 * * * ?")
//    @Scheduled(fixedRate = 100000)
    public void scheduled() {
        try {
            log.info("正在定时收集系统信息。");
            Pair<SysMainMetrics, List<SysDiskMetrics>> metrics = collectMetrics();
            sysMetricsStorageService.insertMetrics(metrics.getFirst(), metrics.getSecond());
        } catch (Exception e) {
            log.error("定时收集系统信息失败：{}", e.getMessage());
        }
    }

}
