package cn.programcx.foxnaserver.api.monitor;

import cn.programcx.foxnaserver.entity.SysMainMetrics;
import cn.programcx.foxnaserver.service.monitor.SysMetricsStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class MonitorStatisticsControllerTest {

    @Mock
    private SysMetricsStorageService sysMetricsStorageService;

    @InjectMocks
    private MonitorStatisticsController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetRecentStatisticsByMillRange_withQueryParams() {
        // Arrange
        List<SysMainMetrics> mockMetrics = new ArrayList<>();
        SysMainMetrics metric = new SysMainMetrics();
        metric.setId(1L);
        metric.setRecordTime(LocalDateTime.now());
        metric.setCpuUsage(50.0);
        mockMetrics.add(metric);

        when(sysMetricsStorageService.selectRecentMetrics(any(), any()))
            .thenReturn(mockMetrics);

        // Act
        ResponseEntity<?> response = controller.getRecentStatisticsByMillRange(
            1707110400000L, 1707114000000L, null
        );

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof List);
        @SuppressWarnings("unchecked")
        List<SysMainMetrics> result = (List<SysMainMetrics>) response.getBody();
        assertEquals(1, result.size());
        assertEquals(50.0, result.get(0).getCpuUsage());
    }

    @Test
    void testGetRecentStatisticsByMillRange_withJsonBody() {
        // Arrange
        List<SysMainMetrics> mockMetrics = new ArrayList<>();
        SysMainMetrics metric = new SysMainMetrics();
        metric.setId(1L);
        metric.setRecordTime(LocalDateTime.now());
        metric.setCpuUsage(60.0);
        mockMetrics.add(metric);

        when(sysMetricsStorageService.selectRecentMetrics(any(), any()))
            .thenReturn(mockMetrics);

        MonitorStatisticsController.MetricsRangeRequest request = 
            new MonitorStatisticsController.MetricsRangeRequest();
        request.setStartMills(1707110400000L);
        request.setEndMills(1707114000000L);

        // Act
        ResponseEntity<?> response = controller.getRecentStatisticsByMillRange(
            null, null, request
        );

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof List);
        @SuppressWarnings("unchecked")
        List<SysMainMetrics> result = (List<SysMainMetrics>) response.getBody();
        assertEquals(1, result.size());
        assertEquals(60.0, result.get(0).getCpuUsage());
    }

    @Test
    void testGetRecentStatisticsByMillRange_missingParameters() {
        // Act
        ResponseEntity<?> response = controller.getRecentStatisticsByMillRange(
            null, null, null
        );

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("startMills and endMills are required via query params or JSON body", 
            response.getBody());
    }

    @Test
    void testGetRecentStatisticsByMillRange_serviceException() {
        // Arrange
        when(sysMetricsStorageService.selectRecentMetrics(any(), any()))
            .thenThrow(new RuntimeException("Database error"));

        // Act
        ResponseEntity<?> response = controller.getRecentStatisticsByMillRange(
            1707110400000L, 1707114000000L, null
        );

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("获取最近监控数据失败! ", response.getBody());
    }
}
