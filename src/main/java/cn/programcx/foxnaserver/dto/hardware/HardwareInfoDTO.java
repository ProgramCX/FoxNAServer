package cn.programcx.foxnaserver.dto.hardware;


import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class HardwareInfoDTO {

    // \=\=\= Getter / Setter \=\=\=
    private String operatingSystem;
    private String cpuModel;
    private String cpuVendor;

    private String mainBoardModel;
    private String mainBoardVendor;

    private List<MemoryInfo> memoryList;
    private List<DiskInfo> diskList;
    private List<GpuInfo> gpuList;

    // \=\=\= 内部静态类 \=\=\=
    @Setter
    @Getter
    public static class MemoryInfo {
        private String model;
        private String vendor;
        private double sizeGb;

    }

    @Setter
    @Getter
    public static class DiskInfo {
        private String model;
        private String vendorOrSerial;
        private double sizeGb;

    }

    @Setter
    @Getter
    public static class GpuInfo {
        private String model;
        private String vendor;

    }

}