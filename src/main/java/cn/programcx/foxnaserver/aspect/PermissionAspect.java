package cn.programcx.foxnaserver.aspect;

import cn.programcx.foxnaserver.annotation.CheckFilePermission;
import cn.programcx.foxnaserver.entity.Resource;
import cn.programcx.foxnaserver.exception.NoPermissionException;
import cn.programcx.foxnaserver.mapper.ResourceMapper;
import cn.programcx.foxnaserver.util.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Aspect
@Component
public class PermissionAspect {
    @Autowired
    private ResourceMapper resourceMapper;

    /**
     * 定义切入点，匹配所有使用 @CheckFilePermission 注解的方法
     */
    @Pointcut("@annotation(cn.programcx.foxnaserver.annotation.CheckFilePermission)")
    public void permissionPointcut() {
    }

    /**
     * 环绕通知，检查文件权限
     *
     * @param joinPoint 切入点
     * @return 方法执行结果
     * @throws Throwable 如果方法执行出错
     */
    @Around("permissionPointcut()")
    public Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        List<String> pathToCheck = new ArrayList<>();
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        CheckFilePermission checkFilePermission = method.getAnnotation(CheckFilePermission.class);
        if (checkFilePermission == null) {
            return joinPoint.proceed();
        }

        // 从请求参数中获取路径（支持普通表单和 multipart/form-data）
        for (String paramField : checkFilePermission.paramFields()) {
            String value = null;
            
            // 首先尝试从普通参数获取
            value = request.getParameter(paramField);
            
            // 如果是 multipart 请求，尝试从 ParameterMap 获取
            if (value == null && request.getContentType() != null && request.getContentType().contains("multipart")) {
                Map<String, String[]> paramMap = request.getParameterMap();
                if (paramMap.containsKey(paramField)) {
                    String[] values = paramMap.get(paramField);
                    if (values != null && values.length > 0) {
                        value = values[0];
                    }
                }
            }
            
            if (value != null && !value.isEmpty()) {
                pathToCheck.add(value);
            }
        }

        String[] paramNames = ((MethodSignature) joinPoint.getSignature()).getParameterNames();
        Object[] paramValues = joinPoint.getArgs();

        for (String fieldPath : checkFilePermission.bodyFields()) {

            Object fieldObject = null;

            // 遍历参数，查找请求体中的字段对应的对象
            for (int i = 0; i < paramNames.length; i++) {
                if (fieldPath.equals(paramNames[i])) {
                    fieldObject = paramValues[i];
                    break;
                }
            }

            if (fieldObject == null) {
                continue;
            }

            if (fieldObject instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof String os) {
                        pathToCheck.add(os);
                        continue;
                    }
                    if (o instanceof Map<?, ?> map) {
                        for(String bodyMapKeyName : checkFilePermission.bodyMapKeyNames()){
                            pathToCheck.add(map.get(bodyMapKeyName).toString());
                        }
                    }
                }
            }

            if (fieldObject instanceof Map<?, ?> map) {
                for(String bodyMapKeyName : checkFilePermission.bodyMapKeyNames()){
                    pathToCheck.add(map.get(bodyMapKeyName).toString());
                }
            }
        }

        for (int i = 0; i < paramNames.length; i++) {
            for (String target : checkFilePermission.paramNames()) {
                if (paramNames[i].equals(target)) {
                    Object val = paramValues[i];
                    if (val instanceof String s) pathToCheck.add(s);
                    else if (val instanceof List<?> list) {
                        for (Object item : list) {
                            if (item instanceof String s) {
                                pathToCheck.add(s);
                            }
                        }
                    }
                    ;
                }
            }
        }

        // 检查权限
        for (String path : pathToCheck) {
            String normalizedPath = Paths.get(path).normalize().toString();
            if (normalizedPath.isEmpty()) continue;
            String methodType = checkFilePermission.type();
            String userName = JwtUtil.getCurrentUsername();

            if (!hasPermission(userName, normalizedPath, methodType)) {
                throw new NoPermissionException("没有权限操作或访问路径: " + normalizedPath);
            }
        }

        return joinPoint.proceed();
    }

    /**
     * 检查用户是否有访问指定目录的权限
     *
     * @param userName  用户名
     * @param directory 目录路径
     * @param method    权限类型（如 "Read", "Write", "Delete"）
     * @return 是否有权限
     * @throws IOException 如果路径处理出错
     */

    private boolean hasPermission(String userName, String directory, String method) throws IOException {
        LambdaQueryWrapper<Resource> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Resource::getOwnerName, userName).eq(Resource::getPermissionType, method);
        List<Resource> resources = resourceMapper.selectList(queryWrapper);

        Path normalizedPath = Paths.get(directory).normalize();

        if (normalizedPath.toString().contains("..")) {
            return false;
        }

        if (containsSymlink(normalizedPath)) {
            return false;
        }

        for (Resource resource : resources) {
            Path allowedPath = Paths.get(resource.getFolderName()).normalize();
            if (normalizedPath.startsWith(allowedPath)) {
                return true;
            }
        }
        return false;
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
