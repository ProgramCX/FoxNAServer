package cn.programcx.foxnaserver.api.file;

import cn.programcx.foxnaserver.dto.file.AuthedDir;
import cn.programcx.foxnaserver.entity.Resource;
import cn.programcx.foxnaserver.mapper.ResourceMapper;
import cn.programcx.foxnaserver.util.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/filePermission")
@Tag(name = "FilePermission", description = "文件权限相关接口")
public class FilePermissionGetController {
    @Autowired
    private ResourceMapper mapper;

    @GetMapping("/getAuthedDirs")
    @Operation(
            summary = "获取已授权目录列表",
            description = "获取当前用户已授权的目录列表，包括目录路径和权限类型"
    )
    @ApiResponse(
            responseCode = "200",
            description = "成功获取已授权目录列表"
    )
    public ResponseEntity<List<AuthedDir>> getAuthedDirs() {
        LambdaQueryWrapper<Resource> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(Resource::getOwnerUuid, JwtUtil.getCurrentUuid());
        List<Resource> resources = mapper.selectList(lambdaQueryWrapper);

        // 合并权限
        Map<String, Set<String>> pathToPermissions = new HashMap<>();
        for (Resource resource : resources) {
            String path = resource.getFolderName();
            String permission = resource.getPermissionType();
            pathToPermissions
                    .computeIfAbsent(path, k -> new HashSet<>())
                    .add(permission);
        }

        List<AuthedDir> authedDirs = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : pathToPermissions.entrySet()) {
            AuthedDir dto = new AuthedDir();
            dto.setPath(entry.getKey());
            dto.setPermissions(String.join(",", entry.getValue()));
            authedDirs.add(dto);
        }

        log.info("[{}]获取已授权目录列表成功！", JwtUtil.getCurrentUuid());
        return ResponseEntity.ok(authedDirs);
    }
}
