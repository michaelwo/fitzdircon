package org.fitzdircon.device.ifit2;

import org.fitzdircon.console.ifit2.CommandTransport;

public final class GrpcTreadmillDevice extends GrpcDevice {

    public GrpcTreadmillDevice(CommandTransport transport) { super(transport); }

    @Override public String displayName() { return "iFit2 Treadmill"; }
}
