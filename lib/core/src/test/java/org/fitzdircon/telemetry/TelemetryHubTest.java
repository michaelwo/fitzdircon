package org.fitzdircon.telemetry;

import org.fitzdircon.telemetry.InclineTelemetry;
import org.fitzdircon.telemetry.Telemetry;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.*;

public class TelemetryHubTest {

    @Test
    public void subscribe_startsReaderOnceAndFansOutPackets() throws Exception {
        FakeReader reader = new FakeReader();
        TelemetryHub hub = new TelemetryHub(reader);
        List<Telemetry> first = new ArrayList<>();
        List<Telemetry> second = new ArrayList<>();

        TelemetryHub.Subscription firstSub = hub.subscribe(first::add);
        hub.subscribe(second::add);
        reader.emit(new InclineTelemetry(4.0f));

        assertEquals(1, reader.readCount);
        assertEquals(1, first.size());
        assertEquals(1, second.size());

        firstSub.close();
        reader.emit(new InclineTelemetry(5.0f));

        assertEquals(1, first.size());
        assertEquals(2, second.size());
        assertEquals(1, hub.subscriberCount());
    }

    private static final class FakeReader implements TelemetryReader {
        int readCount = 0;
        Consumer<Telemetry> listener;

        @Override
        public void read() throws IOException {
            readCount++;
        }

        @Override
        public boolean subscribe(Consumer<Telemetry> listener) {
            this.listener = listener;
            return true;
        }

        void emit(Telemetry telemetry) {
            listener.accept(telemetry);
        }
    }
}
