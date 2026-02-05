# Monitor Statistics API

This module provides endpoints to query historical system monitoring metrics.

## Database Setup

Before using the monitor statistics feature, you need to create the required database table. Run the following SQL script:

```bash
mysql -u your_username -p your_database < migration/monitor/create_sys_main_metrics_table.sql
```

Or execute the SQL directly:

```sql
CREATE TABLE IF NOT EXISTS `sys_main_metrics` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Primary key, auto-increment',
  `record_time` DATETIME NOT NULL COMMENT 'Time when the metrics were recorded',
  `cpu_usage` DOUBLE COMMENT 'CPU usage percentage (0-100)',
  `memory_used` DOUBLE COMMENT 'Memory used in bytes',
  `memory_total` DOUBLE COMMENT 'Total memory in bytes',
  `disk_used` DOUBLE COMMENT 'Disk space used in bytes',
  `disk_total` DOUBLE COMMENT 'Total disk space in bytes',
  `network_recv_speed` BIGINT COMMENT 'Network receive speed in bytes per second',
  `network_sent_speed` BIGINT COMMENT 'Network send speed in bytes per second',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation time',
  INDEX `idx_record_time` (`record_time`) COMMENT 'Index for efficient time range queries'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='System monitoring metrics table';
```

## API Endpoints

### GET/POST `/api/monitor/getByMillRange`

Query system metrics within a specified time range.

#### Request Methods

**Method 1: GET with Query Parameters**

```bash
GET /api/monitor/getByMillRange?startMills=1707110400000&endMills=1707114000000
```

**Method 2: POST with JSON Body**

```bash
POST /api/monitor/getByMillRange
Content-Type: application/json

{
  "startMills": 1707110400000,
  "endMills": 1707114000000
}
```

**Method 3: Mixed (Query Params + JSON Body)**

The endpoint can accept parameters from both sources. JSON body values take precedence.

#### Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| startMills | Long | Yes | Start time in milliseconds since epoch |
| endMills | Long | Yes | End time in milliseconds since epoch |

#### Response

**Success (200 OK)**

Returns an array of metrics objects:

```json
[
  {
    "id": 1,
    "recordTime": "2024-02-05T10:00:00",
    "cpuUsage": 45.5,
    "memoryUsed": 8589934592,
    "memoryTotal": 17179869184,
    "diskUsed": 107374182400,
    "diskTotal": 214748364800,
    "networkRecvSpeed": 1048576,
    "networkSentSpeed": 524288,
    "createTime": "2024-02-05T10:00:01"
  }
]
```

**Error Responses**

- **400 Bad Request**: Missing required parameters
  ```
  startMills and endMills are required via query params or JSON body
  ```

- **500 Internal Server Error**: Server-side error during query execution
  ```
  获取最近监控数据失败!
  ```

## Data Aggregation

The service automatically aggregates metrics based on the time range to optimize data volume:

| Time Range | Grouping Interval |
|------------|-------------------|
| ≤ 1 hour | 1 minute (60 seconds) |
| ≤ 6 hours | 5 minutes (300 seconds) |
| ≤ 24 hours | 10 minutes (600 seconds) |
| ≤ 7 days | 1 hour (3600 seconds) |
| > 7 days | 1 day (86400 seconds) |

Metrics are averaged within each interval to reduce data points while maintaining trends.

## Usage Examples

### Using cURL

**GET request:**
```bash
curl -X GET "http://localhost:8080/api/monitor/getByMillRange?startMills=1707110400000&endMills=1707114000000"
```

**POST request:**
```bash
curl -X POST "http://localhost:8080/api/monitor/getByMillRange" \
  -H "Content-Type: application/json" \
  -d '{"startMills":1707110400000,"endMills":1707114000000}'
```

### Using JavaScript/Fetch

```javascript
// GET request
fetch('/api/monitor/getByMillRange?startMills=1707110400000&endMills=1707114000000')
  .then(response => response.json())
  .then(data => console.log(data));

// POST request
fetch('/api/monitor/getByMillRange', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
  },
  body: JSON.stringify({
    startMills: 1707110400000,
    endMills: 1707114000000
  })
})
  .then(response => response.json())
  .then(data => console.log(data));
```

## Implementation Details

### Components

1. **Entity**: `SysMainMetrics` - JPA entity representing the metrics table
2. **Mapper**: `SysMainMetricsMapper` - MyBatis Plus mapper with custom SQL
3. **Service**: `SysMetricsStorageService` - Business logic for querying and aggregating metrics
4. **Controller**: `MonitorStatisticsController` - REST API endpoint

### Key Features

- Supports both GET and POST methods for flexibility
- Automatic time-based data aggregation
- Efficient database queries with indexed columns
- Comprehensive error handling
- OpenAPI/Swagger documentation included
- Full unit test coverage

## Testing

Unit tests are located in:
- `src/test/java/cn/programcx/foxnaserver/service/monitor/SysMetricsStorageServiceTest.java`
- `src/test/java/cn/programcx/foxnaserver/api/monitor/MonitorStatisticsControllerTest.java`

Run tests with:
```bash
mvn test -Dtest="SysMetricsStorageServiceTest,MonitorStatisticsControllerTest"
```
