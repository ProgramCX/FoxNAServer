package cn.programcx.foxnaserver.service.user;

import cn.programcx.foxnaserver.dto.user.ResourceDTO;
import cn.programcx.foxnaserver.entity.Permission;
import cn.programcx.foxnaserver.entity.Resource;
import cn.programcx.foxnaserver.entity.User;
import cn.programcx.foxnaserver.mapper.PermissionMapper;
import cn.programcx.foxnaserver.mapper.ResourceMapper;
import cn.programcx.foxnaserver.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserManagementService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PermissionMapper permissionMapper;
    @Autowired
    private ResourceMapper resourceMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final List<String> permissionList = List.of("SSH", "USER", "EMAIL", "STREAM", "FILE", "DDNS", "LOG", "TRANSCODE MANAGEMENT");
    private final List<Map<String, String>> permissionDescriptions = List.of(
            Map.of("name", "SSH", "description", "允许使用SSH服务"),
            Map.of("name", "USER", "description", "允许管理用户"),
            Map.of("name", "EMAIL", "description", "允许使用邮件服务"),
            Map.of("name", "STREAM", "description", "允许使用流媒体服务"),
            Map.of("name", "FILE", "description", "允许使用文件服务"),
            Map.of("name", "DDNS", "description", "允许使用动态域名服务"),
            Map.of("name", "LOG", "description", "允许管理日志"),
            Map.of("name", "TRANSCODE MANAGEMENT", "description", "允许管理转码任务")
    );

    public void addUser(User user, List<Permission> permissionList, List<Resource> resourceList) throws Exception {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();

        queryWrapper.eq(User::getUserName, user.getUserName());

        if (userMapper.selectOne(queryWrapper) != null) {
            throw new Exception("用户已经存在！");
        }

        // 生成 UUID
        user.generateId();
        userMapper.insert(user);

        if (permissionList == null) {
            permissionList = new ArrayList<>();
        }
        if (resourceList == null) {
            resourceList = new ArrayList<>();
        }
        permissionList.forEach(permission -> {
            permission.setOwnerUuid(user.getId());
            permissionMapper.insert(permission);
        });

        resourceList.forEach(resource -> {
            resource.setOwnerUuid(user.getId());
            resourceMapper.insert(resource);
        });

    }

    // ==================== 基于 UUID 的方法 ====================

    public void delUserByUuid(String uuid) throws Exception {
        if (uuid.equals("admin")) {
            throw new Exception("admin 用户不能被删除！");
        }
        // 先查询用户
        User user = userMapper.selectById(uuid);
        if (user == null) {
            throw new Exception("用户不存在！");
        }
        if (user.getUserName().equals("admin")) {
            throw new Exception("admin 用户不能被删除！");
        }

        // 删除用户
        userMapper.deleteById(uuid);

        // 删除相关权限
        LambdaQueryWrapper<Permission> permQueryWrapper = new LambdaQueryWrapper<>();
        permQueryWrapper.eq(Permission::getOwnerUuid, uuid);
        permissionMapper.delete(permQueryWrapper);

        // 删除相关资源
        LambdaQueryWrapper<Resource> resQueryWrapper = new LambdaQueryWrapper<>();
        resQueryWrapper.eq(Resource::getOwnerUuid, uuid);
        resourceMapper.delete(resQueryWrapper);
    }

    public void blockUserByUuid(String uuid) throws Exception {
        User user = userMapper.selectById(uuid);
        if (user == null) {
            throw new Exception("用户不存在！");
        }
        if (user.getUserName().equals("admin")) {
            throw new Exception("admin 用户不能被禁用");
        }
        LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(User::getId, uuid).set(User::getState, "disabled");
        userMapper.update(null, updateWrapper);
    }

    public void unblockUserByUuid(String uuid) throws Exception {
        LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(User::getId, uuid).set(User::getState, "enabled");
        userMapper.update(null, updateWrapper);
    }

    public void changePasswordByUuid(String uuid, String newPassword) throws Exception {
        LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(User::getId, uuid).set(User::getPassword, passwordEncoder.encode(newPassword));
        userMapper.update(null, updateWrapper);
    }

    public void grantPermissionByUuid(String uuid, String permissionName) throws Exception {
        if (!permissionList.contains(permissionName)) {
            throw new Exception("权限不存在！");
        }
        User user = userMapper.selectById(uuid);
        if (user == null) {
            throw new Exception("用户不存在！");
        }

        LambdaQueryWrapper<Permission> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Permission::getOwnerUuid, uuid).eq(Permission::getAreaName, permissionName);
        if (permissionMapper.selectList(queryWrapper).size() > 0) {
            throw new Exception("用户已经拥有该权限！");
        }
        Permission permission = new Permission();
        permission.setOwnerUuid(uuid);
        permission.setAreaName(permissionName);
        permissionMapper.insert(permission);
    }

    public void revokePermissionByUuid(String uuid, String permissionName) throws Exception {
        if (!permissionList.contains(permissionName)) {
            throw new Exception("权限不存在！");
        }
        User user = userMapper.selectById(uuid);
        if (user == null) {
            throw new Exception("用户不存在！");
        }

        LambdaQueryWrapper<Permission> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Permission::getOwnerUuid, uuid).eq(Permission::getAreaName, permissionName);
        int deleted = permissionMapper.delete(queryWrapper);
        if (deleted == 0) {
            throw new Exception("用户不拥有该权限！");
        }
    }

    public void grantResourceByUuid(String uuid, String resourcePath, String type) throws Exception {
        User user = userMapper.selectById(uuid);
        if (user == null) {
            throw new Exception("用户不存在！");
        }

        Resource resource = new Resource();
        resource.setOwnerUuid(uuid);
        resource.setFolderName(resourcePath);
        if (type.equalsIgnoreCase("Read")) {
            type = "Read";
        } else if (type.equalsIgnoreCase("Write")) {
            type = "Write";
        } else {
            throw new Exception("权限类型错误！");
        }
        resource.setPermissionType(type);

        LambdaQueryWrapper<Resource> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Resource::getOwnerUuid, uuid)
                .eq(Resource::getFolderName, resourcePath)
                .eq(Resource::getPermissionType, type);
        if (resourceMapper.selectOne(queryWrapper) != null) {
            throw new Exception("用户已经拥有该资源权限！");
        }

        resourceMapper.insert(resource);
    }

    public void revokeResourceByUuid(String uuid, String resourcePath, String type) throws Exception {
        User user = userMapper.selectById(uuid);
        if (user == null) {
            throw new Exception("用户不存在！");
        }

        LambdaQueryWrapper<Resource> queryWrapper = new LambdaQueryWrapper<>();
        if (type.equalsIgnoreCase("Read")) {
            type = "Read";
        } else if (type.equalsIgnoreCase("Write")) {
            type = "Write";
        } else {
            throw new Exception("权限类型错误！");
        }
        queryWrapper.eq(Resource::getOwnerUuid, uuid)
                .eq(Resource::getFolderName, resourcePath)
                .eq(Resource::getPermissionType, type);
        if (resourceMapper.selectOne(queryWrapper) == null) {
            throw new Exception("用户不拥有该资源权限！");
        }
        resourceMapper.delete(queryWrapper);
    }

    public void modifyResourceByUuid(String uuid, String oldResourcePath, String newResourcePath, List<String> typeList) throws Exception {
        User user = userMapper.selectById(uuid);
        if (user == null) {
            throw new Exception("用户不存在！");
        }

        // 删除旧的资源权限
        LambdaQueryWrapper<Resource> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(Resource::getOwnerUuid, uuid)
                .eq(Resource::getFolderName, oldResourcePath);
        resourceMapper.delete(deleteWrapper);

        // 添加新的资源权限
        for (String type : typeList) {
            Resource resource = new Resource();
            resource.setOwnerUuid(uuid);
            resource.setFolderName(newResourcePath);
            if (type.equalsIgnoreCase("Read")) {
                type = "Read";
            } else if (type.equalsIgnoreCase("Write")) {
                type = "Write";
            } else {
                throw new Exception("权限类型错误！");
            }
            resource.setPermissionType(type);
            resourceMapper.insert(resource);
        }
    }

    public void createResourceByUuid(String uuid, String resourcePath, List<String> typeList) throws Exception {
        User user = userMapper.selectById(uuid);
        if (user == null) {
            throw new Exception("用户不存在！");
        }

        for (String type : typeList) {
            Resource resource = new Resource();
            resource.setOwnerUuid(uuid);
            resource.setFolderName(resourcePath);
            if (type.equalsIgnoreCase("Read")) {
                type = "Read";
            } else if (type.equalsIgnoreCase("Write")) {
                type = "Write";
            } else {
                throw new Exception("权限类型错误！");
            }
            resource.setPermissionType(type);
            resourceMapper.insert(resource);
        }
    }

    public void deleteResourceByUuid(String uuid, String resourcePath) throws Exception {
        User user = userMapper.selectById(uuid);
        if (user == null) {
            throw new Exception("用户不存在！");
        }

        LambdaQueryWrapper<Resource> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Resource::getOwnerUuid, uuid)
                .eq(Resource::getFolderName, resourcePath);
        resourceMapper.delete(queryWrapper);
    }

    public List<ResourceDTO> allResourcesByUuid(String uuid) throws Exception {
        User user = userMapper.selectById(uuid);
        if (user == null) {
            throw new Exception("用户不存在！");
        }
        String userName = user.getUserName();

        LambdaQueryWrapper<Resource> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Resource::getOwnerUuid, uuid);
        List<Resource> resources = resourceMapper.selectList(queryWrapper);

        Map<String, List<Resource>> grouped = resources.stream()
                .collect(Collectors.groupingBy(Resource::getFolderName));

        List<ResourceDTO> resourceDTOList = grouped.entrySet().stream()
                .map(entry -> {
                    String folderName = entry.getKey();
                    List<Resource> folderResources = entry.getValue();

                    List<String> types = folderResources.stream()
                            .map(Resource::getPermissionType)
                            .distinct()
                            .collect(Collectors.toList());

                    ResourceDTO dto = new ResourceDTO();
                    dto.setOwnerUuid(uuid);
                    dto.setFolderName(folderName);
                    dto.setTypes(types);
                    return dto;
                })
                .collect(Collectors.toList());

        return resourceDTOList;
    }

    public void updateUser(User user, String uuid) throws Exception {
        User existingUser = userMapper.selectById(uuid);
        if (existingUser == null) {
            throw new Exception("用户不存在！");
        }
        LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(User::getId, uuid)
                .set(User::getUserName, user.getUserName())
                .set(User::getState, user.getState());

        int rows = userMapper.update(null, updateWrapper);
        if (rows == 0) {
            throw new Exception("用户不存在或更新失败！");
        }
    }

    // ==================== 旧的用户名方法（保留兼容） ====================

    @Deprecated
    public void delUser(String userName) throws Exception {
        if (userName.equals("admin")) {
            throw new Exception("admin 用户不能被删除！");
        }
        // 先查询用户的 UUID
        LambdaQueryWrapper<User> userQueryWrapper = new LambdaQueryWrapper<>();
        userQueryWrapper.eq(User::getUserName, userName);
        User user = userMapper.selectOne(userQueryWrapper);
        if (user == null) {
            throw new Exception("用户不存在！");
        }
        String userUuid = user.getId();

        // 删除用户
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUserName, userName);
        userMapper.delete(queryWrapper);

        // 删除相关权限
        LambdaQueryWrapper<Permission> permQueryWrapper = new LambdaQueryWrapper<>();
        permQueryWrapper.eq(Permission::getOwnerUuid, userUuid);
        permissionMapper.delete(permQueryWrapper);

        // 删除相关资源
        LambdaQueryWrapper<Resource> resQueryWrapper = new LambdaQueryWrapper<>();
        resQueryWrapper.eq(Resource::getOwnerUuid, userUuid);
        resourceMapper.delete(resQueryWrapper);
    }

    @Deprecated
    public void blockUser(String userName) throws Exception {
        if (userName.equals("admin")) {
            throw new Exception("admin 用户不能被禁用");
        }
        LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(User::getUserName, userName).set(User::getState, "disabled");
        userMapper.update(null, updateWrapper);
    }

    @Deprecated
    public void unblockUser(String userName) throws Exception {
        LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(User::getUserName, userName).set(User::getState, "enabled");
        userMapper.update(null, updateWrapper);
    }

    @Deprecated
    public void changePassword(String userName, String newPassword) throws Exception {
        LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(User::getUserName, userName).set(User::getPassword, passwordEncoder.encode(newPassword));
        userMapper.update(null, updateWrapper);
    }

    // 获取用户的 UUID
    @Deprecated
    private String getUserUuid(String userName) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUserName, userName);
        User user = userMapper.selectOne(queryWrapper);
        return user != null ? user.getId() : null;
    }

    @Deprecated
    public void grantPermission(String userName, String permissionName) throws Exception {
        if (!permissionList.contains(permissionName)) {
            throw new Exception("权限不存在！");
        }
        String userUuid = getUserUuid(userName);
        if (userUuid == null) {
            throw new Exception("用户不存在！");
        }

        LambdaQueryWrapper<Permission> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Permission::getOwnerUuid, userUuid).eq(Permission::getAreaName, permissionName);
        if (permissionMapper.selectOne(queryWrapper) != null) {
            throw new Exception("用户已经拥有该权限！");
        }
        Permission permission = new Permission();
        permission.setOwnerUuid(userUuid);
        permission.setAreaName(permissionName);
        permissionMapper.insert(permission);
    }

    @Deprecated
    public void revokePermission(String userName, String permissionName) throws Exception {
        if (!permissionList.contains(permissionName)) {
            throw new Exception("权限不存在！");
        }
        String userUuid = getUserUuid(userName);
        if (userUuid == null) {
            throw new Exception("用户不存在！");
        }

        LambdaQueryWrapper<Permission> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Permission::getOwnerUuid, userUuid).eq(Permission::getAreaName, permissionName);
        Permission permission = permissionMapper.selectOne(queryWrapper);
        if (permission == null) {
            throw new Exception("用户不拥有该权限！");
        }
        permissionMapper.delete(queryWrapper);
    }

    @Deprecated
    public void grantResource(String userName, String resourcePath, String type) throws Exception {
        String userUuid = getUserUuid(userName);
        if (userUuid == null) {
            throw new Exception("用户不存在！");
        }

        Resource resource = new Resource();
        resource.setOwnerUuid(userUuid);
        resource.setFolderName(resourcePath);
        if (type.equalsIgnoreCase("Read")) {
            type = "Read";
        } else if (type.equalsIgnoreCase("Write")) {
            type = "Write";
        } else {
            throw new Exception("权限类型错误！");
        }
        resource.setPermissionType(type);

        LambdaQueryWrapper<Resource> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Resource::getOwnerUuid, userUuid)
                .eq(Resource::getFolderName, resourcePath)
                .eq(Resource::getPermissionType, type);
        if (resourceMapper.selectOne(queryWrapper) != null) {
            throw new Exception("用户已经拥有该资源权限！");
        }

        resourceMapper.insert(resource);
    }

    @Deprecated
    public void revokeResource(String userName, String resourcePath, String type) throws Exception {
        String userUuid = getUserUuid(userName);
        if (userUuid == null) {
            throw new Exception("用户不存在！");
        }

        LambdaQueryWrapper<Resource> queryWrapper = new LambdaQueryWrapper<>();
        if (type.equalsIgnoreCase("Read")) {
            type = "Read";
        } else if (type.equalsIgnoreCase("Write")) {
            type = "Write";
        } else {
            throw new Exception("权限类型错误！");
        }
        queryWrapper.eq(Resource::getOwnerUuid, userUuid)
                .eq(Resource::getFolderName, resourcePath)
                .eq(Resource::getPermissionType, type);
        if (resourceMapper.selectOne(queryWrapper) == null) {
            throw new Exception("用户不拥有该资源权限！");
        }
        resourceMapper.delete(queryWrapper);
    }

    @Deprecated
    public void modifyResource(String userName, String oldResourcePath, String newResourcePath, List<String> typeList) throws Exception {
        String userUuid = getUserUuid(userName);
        if (userUuid == null) {
            throw new Exception("用户不存在！");
        }

        // 删除旧的资源权限
        LambdaQueryWrapper<Resource> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(Resource::getOwnerUuid, userUuid)
                .eq(Resource::getFolderName, oldResourcePath);
        resourceMapper.delete(deleteWrapper);

        // 添加新的资源权限
        for (String type : typeList) {
            Resource resource = new Resource();
            resource.setOwnerUuid(userUuid);
            resource.setFolderName(newResourcePath);
            if (type.equalsIgnoreCase("Read")) {
                type = "Read";
            } else if (type.equalsIgnoreCase("Write")) {
                type = "Write";
            } else {
                throw new Exception("权限类型错误！");
            }
            resource.setPermissionType(type);
            resourceMapper.insert(resource);
        }
    }

    @Deprecated
    public void createResource(String userName, String resourcePath, List<String> typeList) throws Exception {
        String userUuid = getUserUuid(userName);
        if (userUuid == null) {
            throw new Exception("用户不存在！");
        }

        for (String type : typeList) {
            Resource resource = new Resource();
            resource.setOwnerUuid(userUuid);
            resource.setFolderName(resourcePath);
            if (type.equalsIgnoreCase("Read")) {
                type = "Read";
            } else if (type.equalsIgnoreCase("Write")) {
                type = "Write";
            } else {
                throw new Exception("权限类型错误！");
            }
            resource.setPermissionType(type);
            resourceMapper.insert(resource);
        }
    }

    @Deprecated
    public void deleteResource(String userName, String resourcePath) throws Exception {
        String userUuid = getUserUuid(userName);
        if (userUuid == null) {
            throw new Exception("用户不存在！");
        }

        LambdaQueryWrapper<Resource> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Resource::getOwnerUuid, userUuid)
                .eq(Resource::getFolderName, resourcePath);
        resourceMapper.delete(queryWrapper);
    }

    @Deprecated
    public List<ResourceDTO> allResources(String userName) throws Exception {
        String userUuid = getUserUuid(userName);
        if (userUuid == null) {
            throw new Exception("用户不存在！");
        }

        LambdaQueryWrapper<Resource> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Resource::getOwnerUuid, userUuid);
        List<Resource> resources = resourceMapper.selectList(queryWrapper);

        Map<String, List<Resource>> grouped = resources.stream()
                .collect(Collectors.groupingBy(Resource::getFolderName));

        List<ResourceDTO> resourceDTOList = grouped.entrySet().stream()
                .map(entry -> {
                    String folderName = entry.getKey();
                    List<Resource> folderResources = entry.getValue();

                    List<String> types = folderResources.stream()
                            .map(Resource::getPermissionType)
                            .distinct()
                            .collect(Collectors.toList());

                    ResourceDTO dto = new ResourceDTO();
                    dto.setOwnerUuid(userUuid);
                    dto.setFolderName(folderName);
                    dto.setTypes(types);
                    return dto;
                })
                .collect(Collectors.toList());

        return resourceDTOList;
    }

    // ==================== 其他方法 ====================

    public List<Map<String, String>> allPermissions() {
        return new ArrayList<>(permissionDescriptions);
    }

    public List<DirectoryDTO> listDirectories(String path) {
        List<DirectoryDTO> directories = new ArrayList<>();

        File dir = new java.io.File(path);

        // 如果 path 为空，列出所有根目录
        if (path.isEmpty()) {
            FileSystem fs = FileSystems.getDefault();
            fs.getRootDirectories().forEach(f -> {
                directories.add(new DirectoryDTO(f.toString().replace(File.separatorChar, ' ').trim(), f.toString().replace(File.separatorChar, '/'),getSubdirectoryCount(f)));
            });

            return directories;
        }

        if (!dir.exists() || !dir.isDirectory()) {
            return directories;
        }

        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    directories.add(new DirectoryDTO(file.getName(), file.toString().replace(File.separatorChar,'/'),getSubdirectoryCount(file)));
                }
            }
        }
        return directories;
    }

    private int getSubdirectoryCount(Path path) {
        // 检查路径是否有效且为目录
        if (Files.notExists(path) || !Files.isDirectory(path)) {
            System.out.println("目录不存在或路径不是一个目录");
            return 0;
        }

        // 统计子目录个数
        int subDirCount = 0;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            for (Path entry : stream) {
                // 检查是否是子目录
                if (Files.isDirectory(entry)) {
                    subDirCount++;
                }
            }
        } catch (IOException e) {
            log.error("读取目录失败：{}", e.getMessage());
            return 0;
        }

        return subDirCount;
    }

    private int getSubdirectoryCount(File dir) {
        // 检查路径是否有效且为目录
        if (!dir.exists() || !dir.isDirectory()) {
            System.out.println("目录不存在或路径不是一个目录");
            return 0;
        }

        // 统计子目录个数
        int subDirCount = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    subDirCount++;
                }
            }
        }
        return subDirCount;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    public static class DirectoryDTO{
        private String name;
        private String path;
        private int childCount;
    }
}
