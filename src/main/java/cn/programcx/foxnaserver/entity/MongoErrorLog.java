package cn.programcx.foxnaserver.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;

/**
 * MongoDB错误日志实体类
 */
@EqualsAndHashCode(callSuper = false)
@Data
@Document(collection = "error_logs")
public class MongoErrorLog {

    @Id
    private String id;

    @Indexed
    private String userName;

    @Indexed
    private String moduleName;

    private String errorMessage;

    private String stackTrace;

    private String uri;

    private String method;

    private String params;

    private String ipAddress;

    @Indexed
    private LocalDateTime createdTime;

    @Indexed
    private String exceptionType;
}
