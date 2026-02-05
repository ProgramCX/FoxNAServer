-- Migration script for sys_main_metrics table
-- This table stores system monitoring metrics for tracking historical performance data

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
