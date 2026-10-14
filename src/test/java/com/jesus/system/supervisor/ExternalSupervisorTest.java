package com.jesus.system.supervisor;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalSupervisorTest {

    @Test
    void recoversTargetAndRestartsWatchdogOnlyAfterThreshold() {
        AtomicInteger recover = new AtomicInteger();
        AtomicInteger restart = new AtomicInteger();
        ExternalSupervisor s = new ExternalSupervisor(() -> false, () -> false,
                recover::incrementAndGet, restart::incrementAndGet, 3);
        s.tick();
        s.tick();
        assertThat(recover.get()).isZero();
        s.tick();
        assertThat(recover.get()).isEqualTo(1);
        assertThat(restart.get()).isEqualTo(1);
    }

    @Test
    void successResetsFailureCount() {
        AtomicInteger recover = new AtomicInteger();
        boolean[] alive = {false};
        ExternalSupervisor s = new ExternalSupervisor(() -> alive[0], () -> true,
                recover::incrementAndGet, () -> { }, 2);
        s.tick();
        alive[0] = true;
        s.tick();
        alive[0] = false;
        s.tick();
        assertThat(recover.get()).isZero();
    }
}
