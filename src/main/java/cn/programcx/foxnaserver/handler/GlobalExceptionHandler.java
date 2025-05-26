package cn.programcx.foxnaserver.handler;

import cn.programcx.foxnaserver.exception.NoPermissionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

     @ExceptionHandler(NoPermissionException.class)
     public ResponseEntity<Map<String,Object>> handleNoPermissionException(NoPermissionException ex) {
         return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                 "code", 403,
                 "message", ex.getMessage()
         ));
     }

}
