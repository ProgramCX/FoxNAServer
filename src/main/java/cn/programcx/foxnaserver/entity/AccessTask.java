package cn.programcx.foxnaserver.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import static io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("tb_ddns_task")
public class AccessTask {

    @TableId(type= IdType.AUTO)
    @Schema(description = "任务ID", example = "1")
    private Long id; // 任务ID

    @Schema(description = "任务名称", example = "re")
    private String taskName; // 任务名称

    @Schema(description = "任务描述", example = "这是一个测试任务")
    private String taskDescription; // 任务描述

    @Schema(description = "IP地址", example = "127.0.0.1")
    private String taskIp;

    @Schema(description = "域名RR记录", example = "www")
    private String domainRr; // 关联的域名

    @Schema(description = "主域名", example = "example.com")
    private String mainDomain; // 主域名

    @Schema(description = "DNS Secret ID", example = "1")
    private Long dnsSecretId; // 关联的访问密钥ID

    @Schema(description = "同步间隔，单位为分钟", example = "1")
    private int syncInterval; // 同步间隔，单位为分钟

    @Schema(description = "任务状态，0-禁用，1-启用", example = "1")
    private int status;

    @Schema(description  = "ip类型", example = "ipv4")
    private String ipType;

    @Schema(description = "是否为公网IP", example = "0")
    private int isPublicIp; // 是否为公网IP，0-否，1-是

    @Schema(description = "上一次是否失败", example = "false")
    private boolean lastFailed; 
}
