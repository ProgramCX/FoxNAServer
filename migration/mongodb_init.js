/**
 * FoxNAS MongoDB 初始化脚本
 * 
 * 使用方法:
 * 1. 打开命令行，进入MongoDB安装目录的bin文件夹
 * 2. 运行: mongosh < path/to/mongodb_init.js
 *    或者: mongosh --file path/to/mongodb_init.js
 *    或者: mongo < path/to/mongodb_init.js (旧版本)
 */

// 切换到 foxnas_logs 数据库（如果不存在会自动创建）
db = db.getSiblingDB('foxnas_logs');

// 删除旧的集合（如果存在）- 可选，首次创建时可以注释掉
// db.error_logs.drop()

// 创建 error_logs 集合，并设置验证规则
db.createCollection("error_logs", {
    validator: {
        $jsonSchema: {
            bsonType: "object",
            required: ["createdTime"],
            properties: {
                _id: {
                    bsonType: "objectId",
                    description: "文档ID"
                },
                userName: {
                    bsonType: ["string", "null"],
                    description: "用户名/UUID"
                },
                moduleName: {
                    bsonType: ["string", "null"],
                    description: "模块名称"
                },
                errorMessage: {
                    bsonType: ["string", "null"],
                    description: "错误消息"
                },
                stackTrace: {
                    bsonType: ["string", "null"],
                    description: "堆栈跟踪信息"
                },
                uri: {
                    bsonType: ["string", "null"],
                    description: "请求URI"
                },
                method: {
                    bsonType: ["string", "null"],
                    description: "HTTP方法"
                },
                params: {
                    bsonType: ["string", "null"],
                    description: "请求参数JSON"
                },
                ipAddress: {
                    bsonType: ["string", "null"],
                    description: "IP地址"
                },
                createdTime: {
                    bsonType: "date",
                    description: "创建时间"
                },
                exceptionType: {
                    bsonType: ["string", "null"],
                    description: "异常类型"
                }
            }
        }
    },
    validationLevel: "moderate",
    validationAction: "warn"
})

print("✓ 集合 error_logs 创建成功")

// 创建索引
db.error_logs.createIndex({ "userName": 1 }, { name: "idx_userName", background: true })
print("✓ 索引 idx_userName 创建成功")

db.error_logs.createIndex({ "moduleName": 1 }, { name: "idx_moduleName", background: true })
print("✓ 索引 idx_moduleName 创建成功")

db.error_logs.createIndex({ "createdTime": -1 }, { name: "idx_createdTime", background: true })
print("✓ 索引 idx_createdTime 创建成功")

db.error_logs.createIndex({ "exceptionType": 1 }, { name: "idx_exceptionType", background: true })
print("✓ 索引 idx_exceptionType 创建成功")

// 创建复合索引（常用查询）
db.error_logs.createIndex(
    { "moduleName": 1, "createdTime": -1 }, 
    { name: "idx_moduleName_createdTime", background: true }
)
print("✓ 复合索引 idx_moduleName_createdTime 创建成功")

db.error_logs.createIndex(
    { "userName": 1, "createdTime": -1 }, 
    { name: "idx_userName_createdTime", background: true }
)
print("✓ 复合索引 idx_userName_createdTime 创建成功")

// 创建TTL索引 - 自动删除90天前的日志（可选，根据需要启用）
// db.error_logs.createIndex(
//     { "createdTime": 1 }, 
//     { name: "idx_ttl_createdTime", expireAfterSeconds: 7776000, background: true }
// )
// print("✓ TTL索引 idx_ttl_createdTime 创建成功（90天后自动删除）")

// 显示集合信息
print("\n========== 数据库信息 ==========")
print("数据库: " + db.getName())
print("集合列表:")
db.getCollectionNames().forEach(function(name) {
    print("  - " + name)
})

print("\n========== error_logs 索引信息 ==========")
db.error_logs.getIndexes().forEach(function(index) {
    print("  - " + index.name + ": " + JSON.stringify(index.key))
})

// 插入一条测试数据（可选）
db.error_logs.insertOne({
    userName: "system",
    moduleName: "init",
    errorMessage: "MongoDB初始化测试日志",
    stackTrace: "",
    uri: "/api/init",
    method: "GET",
    params: "{}",
    ipAddress: "127.0.0.1",
    createdTime: new Date(),
    exceptionType: "TestException"
})
print("\n✓ 测试数据插入成功")

print("\n========== MongoDB 初始化完成 ==========")
