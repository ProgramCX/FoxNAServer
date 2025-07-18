package cn.programcx.foxnaserver.controller.ddns;

import cn.programcx.foxnaserver.dto.ddns.PageAccessSecret;
import cn.programcx.foxnaserver.entity.AccessSecret;
import cn.programcx.foxnaserver.service.ddns.DDNSAccessSecretService;
import cn.programcx.foxnaserver.util.JwtUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/ddns/config")
@Tag(name = "DDNSConfig", description = "DDNS配置相关接口")
public class DDNSConfigController {

    @Autowired
    private DDNSAccessSecretService ddnsAccessSecretService;

    @Operation(
            summary = "获取支持的DNS服务商列表",
            description = "返回当前支持的DNS服务商列表，包括Cloudflare、阿里云、腾讯云和华为云"
    )
    @ApiResponse(
            responseCode = "200",
            description = "成功获取DNS服务商列表"
    )

    @GetMapping("/dnsProviders")
    public List<DnsProviderDTO> getCloudDNSProviders() {
        //目前仅仅支持Cloudflare、阿里云、腾讯云和华为云
        log.info("[{}]获取DNS服务商列表成功", System.currentTimeMillis());
        return List.of(
                new DnsProviderDTO("Cloudflare", "cloudflare", 1),
                new DnsProviderDTO("阿里云", "aliyun", 2),
                new DnsProviderDTO("腾讯云", "tencent", 3),
                new DnsProviderDTO("华为云", "huawei", 4)
        );
    }

    @Operation(
            summary = "获取DDNS访问密钥列表",
            description = "分页获取DDNS访问密钥列表，默认每页10条"
    )
    @ApiResponse(
            responseCode = "200",
            description = "成功获取DDNS访问密钥列表",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = PageAccessSecret.class)
            )
    )
    @GetMapping("/accessKeys")
    public ResponseEntity<?> getAccessKeys(@RequestParam(defaultValue = "1", value = "currentPage") int currentPage, @RequestParam(defaultValue = "10", value = "pageSize") int pageSize) {
        return ResponseEntity.ok(ddnsAccessSecretService.page(new Page<>(currentPage, pageSize)));
    }

    @Operation(summary = "添加DDNS访问密钥", description = "添加新的DDNS访问密钥")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "成功添加DDNS访问密钥",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "AccessKey 添加成功")
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "添加DDNS访问密钥失败",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "AccessKey 添加失败")
                    )
            )
    })
    @PostMapping("/addAccessKey")
    public ResponseEntity<String> addAccessKey(@RequestBody AccessSecret accessSecret) {
        boolean saved = ddnsAccessSecretService.save(accessSecret);
        if (saved) {
            log.info("[{}]添加DDNS访问密钥成功: id为{}", JwtUtil.getCurrentUsername(), accessSecret.getId());
            return ResponseEntity.ok("AccessKey 添加成功");
        } else {
            log.info("[{}]添加DDNS访问密钥失败: id为{}", JwtUtil.getCurrentUsername(), accessSecret.getId());
            return ResponseEntity.status(500).body("AccessKey 添加失败");
        }
    }


    @Operation(summary = "删除DDNS访问密钥", description = "删除现有的DDNS访问密钥")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "删除更新DDNS访问密钥",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "AccessKey 删除成功")
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "删除DDNS访问密钥失败",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "AccessKey 删除失败")
                    )
            )
    })
    @DeleteMapping("/delAccessKey")
    public ResponseEntity<String> delAccessKey(@RequestParam("id") String id) {
        boolean removed = ddnsAccessSecretService.removeById(id);
        if (removed) {
            log.info("[{}]删除DDNS访问密钥成功: id为{}", JwtUtil.getCurrentUsername(), id);
            return ResponseEntity.ok("AccessKey 删除成功");
        } else {
            log.info("[{}]删除DDNS访问密钥失败: id为{}", JwtUtil.getCurrentUsername(), id);
            return ResponseEntity.status(500).body("AccessKey 删除失败");
        }
    }



    @AllArgsConstructor
    @Data
    public static class DnsProviderDTO {
        @Schema(description = "DNS服务商名称", example = "Cloudflare")
        private String name;
        @Schema(description = "DNS服务商标识符", example = "cloudflare")
        private String value;
        @Schema(description = "DNS服务商代码", example = "1")
        private Integer code;

    }
}
