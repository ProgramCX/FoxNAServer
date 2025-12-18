package cn.programcx.foxnaserver.util;

import cn.programcx.foxnaserver.dto.hardware.HardwareInfoDTO;
import oshi.SystemInfo;
import oshi.hardware.*;
import oshi.software.os.OperatingSystem;

import java.util.ArrayList;
import java.util.List;

public class HardwareInfoUtil {

    private static final SystemInfo SYSTEM_INFO = new SystemInfo();
    private static final HardwareAbstractionLayer HAL = SYSTEM_INFO.getHardware();
    private static final OperatingSystem OS = SYSTEM_INFO.getOperatingSystem();

    public static HardwareInfoDTO collectHardwareInfo() {
        HardwareInfoDTO dto = new HardwareInfoDTO();

        // 操作系统
        dto.setOperatingSystem(OS.toString());

        // CPU
        CentralProcessor cpu = HAL.getProcessor();
        dto.setCpuModel(cpu.getProcessorIdentifier().getName());
        dto.setCpuVendor(cpu.getProcessorIdentifier().getVendor());

        // 主板
        ComputerSystem computerSystem = HAL.getComputerSystem();
        Baseboard baseboard = computerSystem.getBaseboard();
        dto.setMainBoardModel(baseboard.getModel());
        dto.setMainBoardVendor(baseboard.getManufacturer());

        // 内存条
        List<HardwareInfoDTO.MemoryInfo> memoryInfos = new ArrayList<>();
        for (PhysicalMemory pm : HAL.getMemory().getPhysicalMemory()) {
            HardwareInfoDTO.MemoryInfo mem = new HardwareInfoDTO.MemoryInfo();
            mem.setModel(pm.getManufacturer() + " " + pm.getMemoryType() + " " + pm.getClockSpeed() / 1_000_000 + "MHz");
            mem.setVendor(pm.getManufacturer());
            mem.setSizeGb(bytesToGb(pm.getCapacity()));
            memoryInfos.add(mem);
        }
        dto.setMemoryList(memoryInfos);

        // 硬盘
        List<HardwareInfoDTO.DiskInfo> diskInfos = new ArrayList<>();
        for (HWDiskStore disk : HAL.getDiskStores()) {
            HardwareInfoDTO.DiskInfo d = new HardwareInfoDTO.DiskInfo();
            d.setModel(disk.getModel());
            // 有些厂商信息在 model，中有些在 serial 中，这里简单取 serial 作为厂商/标识
            d.setVendorOrSerial(disk.getSerial());
            d.setSizeGb(bytesToGb(disk.getSize()));
            diskInfos.add(d);
        }
        dto.setDiskList(diskInfos);

        // 显卡
        List<HardwareInfoDTO.GpuInfo> gpuInfos = new ArrayList<>();
        for (GraphicsCard gc : HAL.getGraphicsCards()) {
            HardwareInfoDTO.GpuInfo g = new HardwareInfoDTO.GpuInfo();
            g.setModel(gc.getName());
            g.setVendor(gc.getVendor());
            gpuInfos.add(g);
        }
        dto.setGpuList(gpuInfos);

        return dto;
    }

    private static double bytesToGb(long bytes) {
        return Math.round(bytes / 1024.0 / 1024 / 1024 * 10) / 10.0; // 保留1位小数
    }
}
