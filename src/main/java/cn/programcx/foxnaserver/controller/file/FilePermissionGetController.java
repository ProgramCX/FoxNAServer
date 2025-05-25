package cn.programcx.foxnaserver.controller.file;

import cn.programcx.foxnaserver.entity.Resource;
import cn.programcx.foxnaserver.mapper.ResourceMapper;
import cn.programcx.foxnaserver.util.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/api/filePermission")
public class FilePermissionGetController {
    @Autowired
    private ResourceMapper mapper;
    @GetMapping("/getAuthedDirs")
    public ResponseEntity<?> getAuthedDirs() {
        LambdaQueryWrapper<Resource> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(Resource::getOwnerName, JwtUtil.getCurrentUsername());
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

        List<Map<String, Object>> authedDirs = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : pathToPermissions.entrySet()) {
            Map<String, Object> map = new HashMap<>();
            map.put("path", entry.getKey());
            map.put("permissions", String.join(",", entry.getValue())); // 逗号拼接权限
            authedDirs.add(map);
        }

        return ResponseEntity.ok(authedDirs);
    }
}
