package cn.programcx.foxnaserver.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = false)
@Data
@TableName("tb_ddns_access_secret")
public class AccessSecret {
    @Schema(
            description = "访问密钥ID",
            example = "1")
    private Long id;

    @Schema(
            description = "访问密钥Key",
            example = "1234567890abcdef"
    )
    private String accessKey;

    @Schema(
            description = "访问密钥Secret",
            example = "abcdef1234567890"
    )
    private String accessSecret;

    @Schema(
            description = "访问名称",
            example = "Cloudflare DDNS Access"
    )
    private String accessName;

    @Schema(
            description = "访问描述",
            example = "用访问密钥"
    )
    private String accessDescription;

    @Schema(
            description = "所属DNS服务商代码",
            example = "enabled"
    )
    private Integer dnsCode; // DNS服务商代码
}
