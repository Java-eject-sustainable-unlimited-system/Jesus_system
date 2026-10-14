package com.jesus.system.monitor;

import com.jesus.system.config.WatchdogProperties;
import com.jesus.system.core.ProcessLifecycleManager;
import com.jesus.system.core.VitalsRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

import static org.mockito.Mockito.*;

class HeartbeatMonitorTest {

    @TempDir Path dir;
    private final WatchdogProperties props = new WatchdogProperties();
    private final ProcessLifecycleManager lifecycle = mock(ProcessLifecycleManager.class);

    private HeartbeatMonitor monitor(Path file) {
        props.setHeartbeatFile(file.toString());
        props.setHeartbeatMaxAge(Duration.ofSeconds(10));
        return new HeartbeatMonitor(props, new VitalsRegistry(), lifecycle);
    }

    @Test
    void disabledWhenBlank() {
        new HeartbeatMonitor(props, new VitalsRegistry(), lifecycle).checkHeartbeat();
        verifyNoInteractions(lifecycle);
    }

    @Test
    void freshHeartbeatDoesNotEject() throws Exception {
        Path f = Files.createFile(dir.resolve("hb"));
        var m = monitor(f);
        for (int i = 0; i < 5; i++) m.checkHeartbeat();
        verifyNoInteractions(lifecycle);
    }

    @Test
    void staleHeartbeatEjectsAfterThreshold() throws Exception {
        Path f = Files.createFile(dir.resolve("hb"));
        Files.setLastModifiedTime(f, FileTime.from(Instant.now().minusSeconds(60)));
        var m = monitor(f);
        for (int i = 0; i < props.getFailureThreshold(); i++) m.checkHeartbeat();
        verify(lifecycle).ejectAndResurrect("HEARTBEAT_STALE");
    }

    @Test
    void missingFileCountsAsFailure() {
        var m = monitor(dir.resolve("absent"));
        for (int i = 0; i < props.getFailureThreshold(); i++) m.checkHeartbeat();
        verify(lifecycle).ejectAndResurrect("HEARTBEAT_STALE");
    }
}
