package dev.comfyfluffy.caustica.rt.pipeline;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DlssgRuntimeStateMachineTest {
    @Test
    void recoveryNeedsFourValidFramesAndVendorConfirmation() {
        DlssgRuntimeStateMachine machine = new DlssgRuntimeStateMachine();
        assertTrue(machine.transition(DlssgRuntimeState.IDLE, DlssgTransitionReason.NONE, 0, 0));
        assertTrue(machine.transition(DlssgRuntimeState.WARMUP, DlssgTransitionReason.NONE, 1, 0));
        for (int i = 0; i < 4; i++) machine.onValidSourceFrame(i, i);
        assertEquals(DlssgRuntimeState.RECOVERING, machine.state());
        machine.onGeneratedPresentationConfirmed(5, 5);
        assertEquals(DlssgRuntimeState.ACTIVE, machine.state());
    }

    @Test
    void oneTransientErrorDoesNotBecomeFatal() {
        DlssgRuntimeStateMachine machine = new DlssgRuntimeStateMachine();
        assertFalse(machine.recordVendorError(7, 1, 1));
        assertNotEquals(DlssgRuntimeState.FATAL, machine.state());
    }
}
