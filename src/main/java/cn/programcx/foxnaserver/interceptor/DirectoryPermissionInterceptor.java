package cn.programcx.foxnaserver.interceptor;

import cn.programcx.foxnaserver.entity.Resource;
import cn.programcx.foxnaserver.mapper.ResourceMapper;
import cn.programcx.foxnaserver.util.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Component
public class DirectoryPermissionInterceptor implements HandlerInterceptor {
    @Autowired
    ResourceMapper resourceMapper;


    private boolean hasPermission(String userUuid, String directory, String method) throws IOException {
        LambdaQueryWrapper<Resource> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Resource::getOwnerUuid, userUuid).eq(Resource::getPermissionType, method);
        List<Resource> resources = resourceMapper.selectList(queryWrapper);

        String normalizedPath = Paths.get(directory).normalize().toString();

        String raw = directory.replace("\\", "/");
        if (raw.contains("../")) {
            return false;
        }

        if(containsSymlink(Paths.get(raw))){
            return false;
        }

        for (Resource resource : resources) {
            String allowedPath = Paths.get(resource.getFolderName()).normalize().toString();
            if (normalizedPath.startsWith(allowedPath)) {
                return true;
            }
        }
        return false;
    }


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String userUuid = JwtUtil.getCurrentUuid();
        String path = request.getParameter("path");
        String methodType = request.getMethod();

        if (path == null || path.isEmpty()) {
            String newPath = request.getParameter("newPath");
            String oldPath = request.getParameter("oldPath");
            if (newPath == null || newPath.isEmpty() || oldPath == null || oldPath.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return false;
            }

            if(!hasPermission(userUuid, oldPath, "Read") || !hasPermission(userUuid, newPath, "Write")) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return false;
            }
        }else{

            String method;

            if ("POST".equalsIgnoreCase(methodType)) {
                method = "WRITE";
            } else if ("GET".equalsIgnoreCase(methodType)) {
                method = "READ";
            } else if ("PUT".equalsIgnoreCase(methodType)) {
                method = "WRITE";
            } else if ("DELETE".equalsIgnoreCase(methodType)) {
                method = "WRITE";
            } else if ("PATCH".equalsIgnoreCase(methodType)) {
                method = "WRITE";
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return false;
            }

            if (!hasPermission(userUuid, path, method)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return false;
            }
        }

        return true;

    }

    public boolean containsSymlink(Path path) throws IOException {
        Path current = path;
        while (current != null) {
            if (Files.isSymbolicLink(current)) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }
}
