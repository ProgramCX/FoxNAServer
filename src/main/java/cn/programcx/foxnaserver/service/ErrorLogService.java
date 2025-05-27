package cn.programcx.foxnaserver.service;

import cn.programcx.foxnaserver.entity.ErrorLog;
import cn.programcx.foxnaserver.mapper.ErrorLogMapper;
import cn.programcx.foxnaserver.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Date;

@Slf4j
@Service
public class ErrorLogService {
    @Autowired
    private ErrorLogMapper errorLogMapper;

    public void insertErrorLog(HttpServletRequest request, Exception ex,String message) {
        try{
            ErrorLog errorLog = new ErrorLog();
            errorLog.setModuleName(getModuleName(request));
            errorLog.setErrorMessage(message);
            errorLog.setParams(getParamAsJSON(request));
            errorLog.setMethod(request.getMethod());
            errorLog.setUri(request.getRequestURI());
            errorLog.setIpAddress(request.getRemoteAddr());
            errorLog.setStackTrace(getStackTraceAsString(ex));
            errorLog.setExceptionType(ex.getClass().getName());
            errorLog.setUserName(JwtUtil.getCurrentUsername());
            errorLog.setCreatedTime(LocalDateTime.now());
            errorLogMapper.insert(errorLog);
            log.error("Error occurred in module: {}, URI: {}, Method: {}, Params: {}, IP: {}, Message: {}, StackTrace: {}",
                    errorLog.getModuleName(), errorLog.getUri(), errorLog.getMethod(),
                    errorLog.getParams(), errorLog.getIpAddress(), errorLog.getErrorMessage(), errorLog.getStackTrace());
            log.info("{}-插入错误日志成功: {}", errorLog.getId(), message);
        } catch (Exception e) {
            log.error("插入错误日志失败", e);
        }
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
            builder.append(stackTraceElement.toString() + "\n");
        }
        return builder.toString();
    }

    private String getModuleName(HttpServletRequest request) {
        String[] segments = request.getRequestURI().split("/");
        return segments.length > 2 ? segments[2] : "unknown";
    }

}
