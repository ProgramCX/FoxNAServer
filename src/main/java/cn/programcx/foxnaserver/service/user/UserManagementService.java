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

    private final List<String> permissionList = List.of("SSH", "USER", "EMAIL", "STREAM", "FILE", "DDNS");
    private final List<Map<String, String>> permissionDescriptions = List.of(
            Map.of("name", "SSH", "description", "允许使用SSH服务"),
            Map.of("name", "USER", "description", "允许管理用户"),
            Map.of("name", "EMAIL", "description", "允许使用邮件服务"),
            Map.of("name", "STREAM", "description", "允许使用流媒体服务"),
            Map.of("name", "FILE", "description", "允许使用文件服务"),
            Map.of("name", "DDNS", "description", "允许使用动态域名服务")
    );

    public void addUser(User user, List<Permission> permissionList, List<Resource> resourceList) throws Exception {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();


        System.out.println(user.getPassword());
        queryWrapper.eq(User::getUserName, user.getUserName());

        if (userMapper.selectOne(queryWrapper) != null) {
            throw new Exception("用户已经存在！");
        }

        userMapper.insert(user);

        if (permissionList == null) {
            permissionList = new ArrayList<>();
        }
        if (resourceList == null) {
            resourceList = new ArrayList<>();
        }
        permissionList.forEach(permission -> {
            permissionMapper.insert(permission);
        });

        resourceList.forEach(resource -> {
            resourceMapper.insert(resource);
        });

    }

    public void delUser(String userName) throws Exception {
        if (userName.equals("admin")) {
            throw new Exception("admin 用户不能被删除！");
        }
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUserName, userName);
        userMapper.delete(queryWrapper);
    }

    public void blockUser(String userName) throws Exception {
        if (userName.equals("admin")) {
            throw new Exception("admin 用户不能被禁用");
        }
        LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(User::getUserName, userName).set(User::getState, "disabled");
        userMapper.update(null, updateWrapper);
    }

    public void unblockUser(String userName) throws Exception {
        LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(User::getUserName, userName).set(User::getState, "enabled");
        userMapper.update(null, updateWrapper);
    }

    public void changePassword(String userName, String newPassword) throws Exception {
        LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(User::getUserName, userName).set(User::getPassword, newPassword);
        userMapper.update(null, updateWrapper);
    }


    public void grantPermission(String userName, String permissionName) throws Exception {
        if (!permissionList.contains(permissionName)) {
            throw new Exception("权限不存在！");
        }
        LambdaQueryWrapper<Permission> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Permission::getOwnerName, userName).eq(Permission::getAreaName, permissionName);
        if (permissionMapper.selectOne(queryWrapper) != null) {
            throw new Exception("用户已经拥有该权限！");
        }
        Permission permission = new Permission();
        permission.setOwnerName(userName);
        permission.setAreaName(permissionName);
        permissionMapper.insert(permission);
    }

    public void revokePermission(String userName, String permissionName) throws Exception {
        if (!permissionList.contains(permissionName)) {
            throw new Exception("权限不存在！");
        }
        LambdaQueryWrapper<Permission> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Permission::getOwnerName, userName).eq(Permission::getAreaName, permissionName);
        Permission permission = permissionMapper.selectOne(queryWrapper);
        if (permission == null) {
            throw new Exception("用户不拥有该权限！");
        }
        permissionMapper.delete(queryWrapper);
    }

    public List<Map<String, String>> allPermissions() {
        return new ArrayList<>(permissionDescriptions);
    }


    public void updateUser(User user, String originalName) throws Exception {
        LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(User::getUserName, originalName)
                .set(User::getUserName, user.getUserName())
                .set(User::getState, user.getState());

        int rows = userMapper.update(null, updateWrapper);
        if (rows == 0) {
            throw new Exception("用户不存在或更新失败！");
        }
    }

    public void grantResource(String userName, String resourcePath, String type) throws Exception {
        Resource resource = new Resource();
        resource.setOwnerName(userName);
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
        queryWrapper.eq(Resource::getOwnerName, userName)
                .eq(Resource::getFolderName, resourcePath)
                .eq(Resource::getPermissionType, type);
        if (resourceMapper.selectOne(queryWrapper) != null) {
            throw new Exception("用户已经拥有该资源权限！");
        }

        resourceMapper.insert(resource);
    }

    public void revokeResource(String userName, String resourcePath, String type) throws Exception {
        LambdaQueryWrapper<Resource> queryWrapper = new LambdaQueryWrapper<>();
        if (type.equalsIgnoreCase("Read")) {
            type = "Read";
        } else if (type.equalsIgnoreCase("Write")) {
            type = "Write";
        } else {
            throw new Exception("权限类型错误！");
        }
        queryWrapper.eq(Resource::getOwnerName, userName)
                .eq(Resource::getFolderName, resourcePath)
                .eq(Resource::getPermissionType, type);
        if (resourceMapper.selectOne(queryWrapper) == null) {
            throw new Exception("用户不拥有该资源权限！");
        }
        resourceMapper.delete(queryWrapper);
    }


    public List<ResourceDTO> allResources(String userName) throws Exception {
        LambdaQueryWrapper<Resource> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Resource::getOwnerName, userName);
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
                    dto.setOwnerName(userName);
                    dto.setFolderName(folderName);
                    dto.setTypes(types);
                    return dto;
                })
                .collect(Collectors.toList());

        return resourceDTOList;
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

