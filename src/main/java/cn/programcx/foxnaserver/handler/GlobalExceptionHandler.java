package cn.programcx.foxnaserver.handler;

import cn.programcx.foxnaserver.exception.NoPermissionException;
import cn.programcx.foxnaserver.service.log.ErrorLogService;
import cn.programcx.foxnaserver.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @Autowired
    private ErrorLogService errorLogService;

    @ExceptionHandler(NoPermissionException.class)
    public ResponseEntity<Map<String, Object>> handleNoPermissionException(NoPermissionException ex,HttpServletRequest request) {
        log.error("[{}]用户没有权限访问资源: {}", JwtUtil.getCurrentUsername(), ex.getMessage());
        errorLogService.insertErrorLog(request, ex, "用户没有权限访问资源: " + ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "code", 403,
                "message", ex.getMessage()
        ));
    }
//
//    @ExceptionHandler(Exception.class)
//    public void handleException(Exception ex, HttpServletRequest request) {
//        if(ex==null){
//            errorLogService.insertErrorLog(request, new Exception(""),"未知错误");
//        }
//        else {
//            errorLogService.insertErrorLog(request, ex, ex.getMessage());
//        }
//
//    }


}
