package cn.programcx.foxnaserver;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@MapperScan("cn.programcx.foxnaserver.mapper")
@ComponentScan(basePackages = "cn.programcx")
@SpringBootApplication
public class FoxNaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FoxNaServerApplication.class, args);
    }

}
