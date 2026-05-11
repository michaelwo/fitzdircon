package org.fitzdircon.device.ifit2;

import org.fitzdircon.console.ifit2.CommandTransport;

public final class GrpcBikeDevice extends GrpcDevice {

    public GrpcBikeDevice(CommandTransport transport) { super(transport); }

    @Override public String displayName() { return "iFit2 Bike"; }
}
