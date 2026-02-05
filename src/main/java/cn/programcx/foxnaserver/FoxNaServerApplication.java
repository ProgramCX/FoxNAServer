package cn.programcx.foxnaserver;

import com.sun.jna.Memory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OperatingSystem;

@MapperScan("cn.programcx.foxnaserver.mapper")
@ComponentScan(basePackages = "cn.programcx")
@EnableScheduling
@SpringBootApplication
public class FoxNaServerApplication {

    public static void main(String[] args) {

        SpringApplication.run(FoxNaServerApplication.class, args);
    }

}
