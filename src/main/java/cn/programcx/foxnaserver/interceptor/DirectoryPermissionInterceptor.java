package cn.programcx.foxnaserver.interceptor;

import cn.programcx.foxnaserver.entity.Resource;
import cn.programcx.foxnaserver.mapper.ResourceMapper;
import cn.programcx.foxnaserver.util.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

@Component
public class DirectoryPermissionInterceptor implements HandlerInterceptor {
    @Autowired
    ResourceMapper resourceMapper;


    private boolean hasPermission(String userName, String directory, String method) {
        LambdaQueryWrapper<Resource> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Resource::getOwnerName, userName).eq(Resource::getPermissionType, method);
        List<Resource> resources = resourceMapper.selectList(queryWrapper);
        for (Resource resource : resources) {
            if (directory.contains(resource.getFolderName()) && directory.startsWith(resource.getFolderName())) {
                return true;
            }
        }
        return false;
    }


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String userName = JwtUtil.getCurrentUsername();
        String path = request.getParameter("path");
        String method = request.getParameter("method");
        if (path == null || path.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return false;
        }
        if(method == null || method.isEmpty()) {
            method = "Read";
        }
        if (!hasPermission(userName, path, method)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        return true;

    }

}

