package cn.programcx.foxnaserver.mapper;

import cn.programcx.foxnaserver.entity.MongoErrorLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MongoDB错误日志Repository
 */
@Repository
public interface ErrorLogMongoRepository extends MongoRepository<MongoErrorLog, String> {

    /**
     * 根据用户名查询日志
     */
    List<MongoErrorLog> findByUserName(String userName);

    /**
     * 根据模块名查询日志
     */
    List<MongoErrorLog> findByModuleName(String moduleName);

    /**
     * 根据异常类型查询日志
     */
    List<MongoErrorLog> findByExceptionType(String exceptionType);

    /**
     * 根据时间范围查询日志
     */
    List<MongoErrorLog> findByCreatedTimeBetween(LocalDateTime start, LocalDateTime end);

    /**
     * 根据模块名分页查询日志
     */
    Page<MongoErrorLog> findByModuleName(String moduleName, Pageable pageable);

    /**
     * 根据用户名分页查询日志
     */
    Page<MongoErrorLog> findByUserName(String userName, Pageable pageable);

    /**
     * 删除指定时间之前的日志
     */
    void deleteByCreatedTimeBefore(LocalDateTime dateTime);
}
