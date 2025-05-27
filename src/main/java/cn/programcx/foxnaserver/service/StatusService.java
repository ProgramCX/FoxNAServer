package cn.programcx.foxnaserver.service;

import cn.programcx.foxnaserver.callback.StatusCallback;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.NetworkIF;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
public class StatusService {

    private static final Set<String> resourceGetIds = ConcurrentHashMap.newKeySet();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    @Getter
    private final Map<String,Object> info = new ConcurrentHashMap<>();

    private final SystemInfo si = new SystemInfo();
    private final CentralProcessor cpu = si.getHardware().getProcessor();
    private final GlobalMemory memory = si.getHardware().getMemory();
    private final OperatingSystem os = si.getOperatingSystem();
    private final List<NetworkIF> networkIFs = si.getHardware().getNetworkIFs();

    private volatile long[] prevCpuTicks = cpu.getSystemCpuLoadTicks();
    private volatile long[] prevNetBytesRecv;
    private volatile long[] prevNetBytesSent;

    private ScheduledFuture<?> scheduledFuture;

    @Setter
    private StatusCallback callback;

    private final Object monitorLock = new Object();

    public void startMonitor(String sessionId) {
            if (!resourceGetIds.isEmpty()) {
                return;
            }

            resourceGetIds.add(sessionId);

            prevNetBytesRecv = new long[networkIFs.size()];
            prevNetBytesSent = new long[networkIFs.size()];
            for (int i = 0; i < networkIFs.size(); i++) {
                networkIFs.get(i).updateAttributes();
                prevNetBytesRecv[i] = networkIFs.get(i).getBytesRecv();
                prevNetBytesSent[i] = networkIFs.get(i).getBytesSent();
            }

            Runnable runnable = ()->{
                collectMetrics();
                    try {
                        if(callback != null) {
                            callback.onStatusCallback(info);
                        }

                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }

            };

        scheduledFuture =  scheduler.scheduleAtFixedRate(runnable, 0, 1, TimeUnit.SECONDS);


    }

    public void stopMonitor(String sessionId) {
        resourceGetIds.remove(sessionId);
            if (resourceGetIds.isEmpty() && scheduledFuture != null && !scheduledFuture.isCancelled()) {
                scheduledFuture.cancel(false);
                scheduledFuture = null;
            }

    }

    private void collectMetrics() {
        try {
            info.put("cpu", getCPUUsage());
            info.put("memory", getMemoryInfo());
            info.put("disk", getDiskInfo());
            info.put("network", getNetworkInfo());
        } catch (Exception e) {
           log.error("获取系统资源信息失败:{}", e.getMessage());
        }
    }

    private double getCPUUsage() {
        long[] ticks = cpu.getSystemCpuLoadTicks();
        double cpuLoad = cpu.getSystemCpuLoadBetweenTicks(prevCpuTicks);
        prevCpuTicks = ticks;
        return cpuLoad * 100;
    }

    private Map<String,Object> getMemoryInfo() {
        double total = memory.getTotal();
        double used = total - memory.getAvailable();
        Map<String,Object> map = new HashMap<>();
        map.put("total", total);
        map.put("used", used);
        return map;
    }

    private List<Map<String, Object>> getDiskInfo() {
        List<OSFileStore> fsList = os.getFileSystem().getFileStores();
        List<Map<String, Object>> list = new ArrayList<>();
        for (OSFileStore fs : fsList) {
            Map<String, Object> map = new HashMap<>();
            map.put("name", fs.getName());
            map.put("total", fs.getTotalSpace());
            map.put("free", fs.getUsableSpace());
            map.put("used", fs.getTotalSpace() - fs.getFreeSpace());
            list.add(map);
        }
        return list;
    }

    private List<Map<String, Object>> getNetworkInfo() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < networkIFs.size(); i++) {
            NetworkIF net = networkIFs.get(i);
            net.updateAttributes();
            if(net.getIPv4addr().length==0) {
                continue;
            }
            long bytesRecv = net.getBytesRecv();
            long bytesSent = net.getBytesSent();

            long recvSpeed = bytesRecv - prevNetBytesRecv[i];
            long sentSpeed = bytesSent - prevNetBytesSent[i];

            prevNetBytesRecv[i] = bytesRecv;
            prevNetBytesSent[i] = bytesSent;

            Map<String, Object> map = new HashMap<>();
            map.put("name", net.getName());
            map.put("ipv4", net.getIPv4addr());
            map.put("ipv6", net.getIPv6addr());
            map.put("recvSpeed", recvSpeed);
            map.put("sentSpeed", sentSpeed);

            list.add(map);
        }
        return list;
    }

}
