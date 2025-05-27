package cn.programcx.foxnaserver.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.sf.jsqlparser.expression.DateTimeLiteralExpression;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = false)
@Data
@TableName("tb_error_log")
public class ErrorLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userName;

    private String moduleName;

    private String errorMessage;

    private String stackTrace;

    private String uri;

    private String method;

    private String params;

    private String ipAddress;

    private LocalDateTime createdTime;

    private String exceptionType;
}
