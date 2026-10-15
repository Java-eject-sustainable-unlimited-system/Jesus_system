package com.jesus.system.api;

import com.jesus.system.config.WatchdogProperties;
import com.jesus.system.core.ProcessLifecycleManager;
import com.jesus.system.core.ProcessLifecycleManager.EjectionReport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WatchdogCommandControllerTest {

    private final ProcessLifecycleManager lifecycleManager = mock(ProcessLifecycleManager.class);
    private final WatchdogProperties properties = new WatchdogProperties();
    private final WatchdogCommandController controller =
            new WatchdogCommandController(lifecycleManager, properties);

    @Test
    void rejectsMissingOrInvalidTokenWithoutTriggeringEjection() {
        properties.setManualControlToken("configured-secret");

        assertUnauthorized(null);
        assertUnauthorized("incorrect-secret");

        verifyNoInteractions(lifecycleManager);
    }

    @Test
    void disablesManualControlWhenNoTokenIsConfigured() {
        assertUnauthorized("any-token");
        verifyNoInteractions(lifecycleManager);
    }

    @Test
    void executesManualEjectionOnlyWithConfiguredToken() {
        properties.setManualControlToken("configured-secret");
        EjectionReport report = new EjectionReport("MANUAL_API_TRIGGER", 2, true, Instant.EPOCH);
        when(lifecycleManager.ejectAndResurrect("MANUAL_API_TRIGGER")).thenReturn(report);

        var response = controller.manualEjectAndResurrect("configured-secret");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsEntry("processesTerminated", 2)
                .containsEntry("resurrectionTriggered", true);
        verify(lifecycleManager).ejectAndResurrect("MANUAL_API_TRIGGER");
    }

    @Test
    void returnsTooManyRequestsWhenRecoveryIsThrottled() {
        properties.setManualControlToken("configured-secret");
        when(lifecycleManager.ejectAndResurrect("MANUAL_API_TRIGGER"))
                .thenReturn(new EjectionReport("MANUAL_API_TRIGGER", 0, false, Instant.EPOCH, true));

        assertThatThrownBy(() -> controller.manualEjectAndResurrect("configured-secret"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    private void assertUnauthorized(String token) {
        assertThatThrownBy(() -> controller.manualEjectAndResurrect(token))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
