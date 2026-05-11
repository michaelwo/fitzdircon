package org.fitzdircon.device;

import org.fitzdircon.command.Command;
import org.fitzdircon.command.ResistanceCommand;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DeviceControllerTest {

    @Test
    public void enqueueCommandDelegatesToDevice() {
        List<Command> received = new ArrayList<>();
        Device fake = new Device() {
            @Override public String displayName() { return "fake"; }
            @Override public void applyCommand(Command cmd) { received.add(cmd); }
        };
        DeviceController controller = new DeviceController(fake);

        controller.enqueueCommand(new ResistanceCommand(5f));

        assertEquals(1, received.size());
        assertEquals(5f, received.get(0).value, 0.001f);
    }

    @Test
    public void shutdownDelegatesToDevice() {
        boolean[] shutdownCalled = { false };
        Device fake = new Device() {
            @Override public String displayName() { return "fake"; }
            @Override public void shutdown() { shutdownCalled[0] = true; }
        };
        DeviceController controller = new DeviceController(fake);

        controller.shutdown();

        assertTrue(shutdownCalled[0]);
    }
}
