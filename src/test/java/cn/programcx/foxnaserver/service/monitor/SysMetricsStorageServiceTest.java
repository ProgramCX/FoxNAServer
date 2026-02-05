package cn.programcx.foxnaserver.service.monitor;

import cn.programcx.foxnaserver.entity.SysMainMetrics;
import cn.programcx.foxnaserver.mapper.SysMainMetricsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SysMetricsStorageServiceTest {

    @Mock
    private SysMainMetricsMapper sysMainMetricsMapper;

    @InjectMocks
    private SysMetricsStorageService sysMetricsStorageService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testSelectRecentMetrics_success() {
        // Arrange
        LocalDateTime startTime = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2024, 1, 1, 1, 0);
        
        List<SysMainMetrics> mockMetrics = new ArrayList<>();
        SysMainMetrics metric = new SysMainMetrics();
        metric.setId(1L);
        metric.setRecordTime(startTime);
        metric.setCpuUsage(50.0);
        metric.setMemoryUsed(4000.0);
        metric.setMemoryTotal(8000.0);
        mockMetrics.add(metric);
        
        when(sysMainMetricsMapper.selectRecentMetrics(
            eq(startTime), eq(endTime), anyInt())
        ).thenReturn(mockMetrics);
        
        // Act
        List<SysMainMetrics> result = sysMetricsStorageService.selectRecentMetrics(startTime, endTime);
        
        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(50.0, result.get(0).getCpuUsage());
        verify(sysMainMetricsMapper, times(1)).selectRecentMetrics(
            eq(startTime), eq(endTime), anyInt()
        );
    }

    @Test
    void testSelectRecentMetrics_emptyResult() {
        // Arrange
        LocalDateTime startTime = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2024, 1, 1, 1, 0);
        
        when(sysMainMetricsMapper.selectRecentMetrics(
            eq(startTime), eq(endTime), anyInt())
        ).thenReturn(new ArrayList<>());
        
        // Act
        List<SysMainMetrics> result = sysMetricsStorageService.selectRecentMetrics(startTime, endTime);
        
        // Assert
        assertNotNull(result);
        assertEquals(0, result.size());
    }
}
