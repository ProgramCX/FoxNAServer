package cn.programcx.foxnaserver;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("cn.programcx.foxnaserver.mapper")
@SpringBootApplication
public class FoxNaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FoxNaServerApplication.class, args);
    }

}
