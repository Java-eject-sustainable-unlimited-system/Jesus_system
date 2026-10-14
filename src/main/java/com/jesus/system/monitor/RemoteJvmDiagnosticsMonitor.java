package com.jesus.system.monitor;

import com.jesus.system.config.WatchdogProperties;
import com.jesus.system.core.ProcessLifecycleManager;
import com.jesus.system.core.VitalsRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Inspects the internals of a <em>separately running</em> target JVM over JMX,
 * so the watchdog survives the target's failure. A refused connection or a
 * response slower than {@code remote-jmx-timeout} (hung JVM) counts as a failure.
 * Disabled while {@code remote-jmx-url} is blank; only loopback hosts are accepted.
 */
@Component
public class RemoteJvmDiagnosticsMonitor {

    private static final Logger log = LoggerFactory.getLogger(RemoteJvmDiagnosticsMonitor.class);
    private static final String COMPONENT = "remote-jvm-diagnostics";

    /** Point-in-time view of the target JVM. */
    public record Snapshot(long heapUsed, long heapMax, int deadlockedThreads) {
    }

    @FunctionalInterface
    interface Sampler {
        Snapshot sample(String jmxUrl) throws Exception;
    }

    private final WatchdogProperties properties;
    private final VitalsRegistry vitals;
    private final ProcessLifecycleManager lifecycleManager;
    private final Sampler sampler;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "remote-jmx-sampler");
        t.setDaemon(true);
        return t;
    });
    private final FailureCounter failures = new FailureCounter();

    @Autowired
    public RemoteJvmDiagnosticsMonitor(WatchdogProperties properties,
                                       VitalsRegistry vitals,
                                       ProcessLifecycleManager lifecycleManager) {
        this(properties, vitals, lifecycleManager, RemoteJvmDiagnosticsMonitor::sampleOverJmx);
    }

    RemoteJvmDiagnosticsMonitor(WatchdogProperties properties, VitalsRegistry vitals,
                                ProcessLifecycleManager lifecycleManager, Sampler sampler) {
        this.properties = properties;
        this.vitals = vitals;
        this.lifecycleManager = lifecycleManager;
        this.sampler = sampler;
    }

    @Scheduled(fixedRate = 5000)
    public void diagnoseRemoteJvm() {
        String url = properties.getRemoteJmxUrl();
        if (url == null || url.isBlank()) {
            return;
        }
        String failure;
        try {
            if (!isLoopback(url)) {
                failure = "JMX URL host is not loopback; refusing to connect";
            } else {
                failure = evaluate(sampleWithTimeout(url, properties.getRemoteJmxTimeout()));
            }
        } catch (TimeoutException e) {
            failure = "Target JVM unresponsive (JMX timeout)";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (Exception e) {
            failure = "JMX unreachable: " + e.getClass().getSimpleName();
        }

        if (failure == null) {
            if (failures.recoverOne() == 0) {
                vitals.reportUp(COMPONENT, "Remote JVM healthy");
            }
            return;
        }
        int count = failures.recordFailure();
        vitals.reportDown(COMPONENT, "%s (%d/%d)".formatted(failure, count, properties.getFailureThreshold()));
        if (failures.hasReached(properties.getFailureThreshold())) {
            log.warn("[🚨 REMOTE JVM] {} — ejecting target.", failure);
            lifecycleManager.ejectAndResurrect("REMOTE_JVM_UNHEALTHY");
            failures.reset();
        }
    }

    private String evaluate(Snapshot s) {
        if (s.deadlockedThreads() > 0) {
            return s.deadlockedThreads() + " deadlocked threads in target";
        }
        if (s.heapMax() > 0
                && (double) s.heapUsed() / s.heapMax() >= properties.getHeapCriticalThreshold()) {
            return "Target heap at %.1f%%".formatted(100.0 * s.heapUsed() / s.heapMax());
        }
        return null;
    }

    private Snapshot sampleWithTimeout(String url, Duration timeout) throws Exception {
        Future<Snapshot> future = executor.submit(() -> sampler.sample(url));
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            throw e.getCause() instanceof Exception ex ? ex : e;
        }
    }

    private static final Pattern HOST = Pattern.compile("://([^:/\\[\\]]*|\\[[^\\]]*\\])");

    /** Every host named anywhere in the URL (including the JNDI part) must be loopback. */
    static boolean isLoopback(String url) {
        try {
            new JMXServiceURL(url);
            Matcher m = HOST.matcher(url);
            boolean found = false;
            while (m.find()) {
                String host = m.group(1);
                if (host.isEmpty()) {
                    continue;
                }
                if (!InetAddress.getByName(host).isLoopbackAddress()) {
                    return false;
                }
                found = true;
            }
            return found;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static Snapshot sampleOverJmx(String url) throws IOException {
        try (JMXConnector connector = JMXConnectorFactory.connect(new JMXServiceURL(url))) {
            MBeanServerConnection conn = connector.getMBeanServerConnection();
            MemoryMXBean memory = ManagementFactory.newPlatformMXBeanProxy(
                    conn, ManagementFactory.MEMORY_MXBEAN_NAME, MemoryMXBean.class);
            ThreadMXBean threads = ManagementFactory.newPlatformMXBeanProxy(
                    conn, ManagementFactory.THREAD_MXBEAN_NAME, ThreadMXBean.class);
            MemoryUsage heap = memory.getHeapMemoryUsage();
            long[] dead = threads.findDeadlockedThreads();
            return new Snapshot(heap.getUsed(), heap.getMax(), dead == null ? 0 : dead.length);
        }
    }
}
