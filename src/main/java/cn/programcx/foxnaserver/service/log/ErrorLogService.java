package cn.programcx.foxnaserver.service.log;

import cn.programcx.foxnaserver.entity.MongoErrorLog;
import cn.programcx.foxnaserver.mapper.ErrorLogMongoRepository;
import cn.programcx.foxnaserver.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class ErrorLogService {
    @Autowired
    private ErrorLogMongoRepository errorLogMongoRepository;

    public void insertErrorLog(HttpServletRequest request, Exception ex, String message) {
        try {
            MongoErrorLog errorLog = new MongoErrorLog();
            errorLog.setModuleName(getModuleName(request));
            errorLog.setErrorMessage(message);
            errorLog.setParams(getParamAsJSON(request));
            errorLog.setMethod(request.getMethod());
            errorLog.setUri(request.getRequestURI());
            errorLog.setIpAddress(request.getRemoteAddr());
            errorLog.setStackTrace(getStackTraceAsString(ex));
            errorLog.setExceptionType(ex.getClass().getName());
            errorLog.setUserName(JwtUtil.getCurrentUuid());
            errorLog.setCreatedTime(LocalDateTime.now());
            errorLogMongoRepository.save(errorLog);
            log.error("Error occurred in module: {}, URI: {}, Method: {}, Params: {}, IP: {}, Message: {}, StackTrace: {}",
                    errorLog.getModuleName(), errorLog.getUri(), errorLog.getMethod(),
                    errorLog.getParams(), errorLog.getIpAddress(), errorLog.getErrorMessage(), errorLog.getStackTrace());
            log.info("{}-插入错误日志成功: {}", errorLog.getId(), message);
        } catch (Exception e) {
            log.error("插入错误日志失败", e);
        }
    }

    public void insertErrorLog(String message, Exception e, String moduleName, String method, String uri, String ipAddress) {
        try {
            MongoErrorLog errorLog = new MongoErrorLog();
            errorLog.setModuleName(moduleName);
            errorLog.setErrorMessage(message);
            errorLog.setParams("");
            errorLog.setMethod(method);
            errorLog.setUri(uri);
            errorLog.setIpAddress(ipAddress);
            errorLog.setStackTrace(getStackTraceAsString(e));
            errorLog.setExceptionType(e.getClass().getName());
            errorLog.setUserName("system");
            errorLog.setCreatedTime(LocalDateTime.now());
            errorLogMongoRepository.save(errorLog);
            log.error("Error occurred in module: {}, URI: {}, Method: {}, Params: {}, IP: {}, Message: {}, StackTrace: {}",
                    errorLog.getModuleName(), errorLog.getUri(), errorLog.getMethod(),
                    errorLog.getParams(), errorLog.getIpAddress(), errorLog.getErrorMessage(), errorLog.getStackTrace());
            log.info("{}-插入错误日志成功: {}", errorLog.getId(), message);
        } catch (Exception ex) {
            log.error("插入错误日志失败", ex);
        }
    }

    /**
     * 查询所有错误日志
     */
    public List<MongoErrorLog> findAll() {
        return errorLogMongoRepository.findAll(Sort.by(Sort.Direction.DESC, "createdTime"));
    }

    /**
     * 分页查询错误日志
     */
    public Page<MongoErrorLog> findAllPaged(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdTime"));
        return errorLogMongoRepository.findAll(pageable);
    }

    /**
     * 根据模块名查询日志
     */
    public List<MongoErrorLog> findByModuleName(String moduleName) {
        return errorLogMongoRepository.findByModuleName(moduleName);
    }

    /**
     * 根据用户名查询日志
     */
    public List<MongoErrorLog> findByUserName(String userName) {
        return errorLogMongoRepository.findByUserName(userName);
    }

    /**
     * 根据时间范围查询日志
     */
    public List<MongoErrorLog> findByTimeRange(LocalDateTime start, LocalDateTime end) {
        return errorLogMongoRepository.findByCreatedTimeBetween(start, end);
    }

    /**
     * 删除指定时间之前的日志（用于日志清理）
     */
    public void deleteOldLogs(LocalDateTime beforeTime) {
        errorLogMongoRepository.deleteByCreatedTimeBefore(beforeTime);
        log.info("已删除 {} 之前的日志", beforeTime);
    }

    private String getParamAsJSON(HttpServletRequest request) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(request.getParameterMap());
        } catch (Exception e) {
            return "{}";
        }
    }

    private String getStackTraceAsString(Exception e) {
        StringBuilder builder = new StringBuilder();
        StackTraceElement[] stackTraceElements = e.getStackTrace();
        for (StackTraceElement stackTraceElement : stackTraceElements) {
            builder.append(stackTraceElement.toString()).append("\n");
        }
        return builder.toString();
    }

    private String getModuleName(HttpServletRequest request) {
        String[] segments = request.getRequestURI().split("/");
        return segments.length > 2 ? segments[2] : "unknown";
    }

}
