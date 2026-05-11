package org.fitzdircon.platform;

import android.content.Context;
import android.util.Log;

import com.ifit.glassos.console.ConsoleServiceGrpc;
import com.ifit.glassos.console.KnownConsoleInfo;
import com.ifit.glassos.console.MachineType;
import com.ifit.glassos.util.Empty;

import org.fitzdircon.console.ifit2.GrpcCommandTransport;
import org.fitzdircon.console.ifit2.GrpcCredentials;
import org.fitzdircon.console.ifit2.GrpcTelemetryReader;
import org.fitzdircon.device.Device;
import org.fitzdircon.device.ifit2.GrpcBikeDevice;
import org.fitzdircon.device.ifit2.GrpcTreadmillDevice;
import org.fitzdircon.telemetry.TelemetryReader;

import java.util.concurrent.TimeUnit;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.okhttp.OkHttpChannelBuilder;

public final class IFitPlatform {

    private static final String LOG_TAG = "FZ:Platform";
    private static final Metadata.Key<String> CLIENT_ID_HEADER =
            Metadata.Key.of("client_id", Metadata.ASCII_STRING_MARSHALLER);

    public enum MachineClass { BIKE, TREADMILL, UNKNOWN }

    public final boolean available;
    public final MachineClass machineClass;
    public final String diagnostics;

    private IFitPlatform(boolean available, MachineClass machineClass, String diagnostics) {
        this.available    = available;
        this.machineClass = machineClass;
        this.diagnostics  = diagnostics;
    }

    public static IFitPlatform detect(Context context) {
        Context appContext = context.getApplicationContext();
        GrpcCredentials credentials;
        try {
            credentials = GrpcCredentials.load(appContext);
        } catch (Exception e) {
            Log.i(LOG_TAG, "iFit2 credentials unavailable: " + e.getMessage());
            return new IFitPlatform(false, MachineClass.UNKNOWN, e.getMessage());
        }
        MachineClass mc = queryMachineClass(credentials);
        Log.i(LOG_TAG, "detected: iFit2 gRPC / " + mc);
        return new IFitPlatform(true, mc, null);
    }

    private static MachineClass queryMachineClass(GrpcCredentials credentials) {
        ManagedChannel channel = null;
        try {
            channel = OkHttpChannelBuilder
                    .forAddress("localhost", 54321)
                    .overrideAuthority("localhost:54321")
                    .sslSocketFactory(credentials.sslContext().getSocketFactory())
                    .hostnameVerifier((hostname, session) -> true)
                    .intercept(clientIdInterceptor())
                    .build();
            KnownConsoleInfo info = ConsoleServiceGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(2, TimeUnit.SECONDS)
                    .getKnownConsoleInfo(Empty.newBuilder().build());
            return toMachineClass(info.getMachineType());
        } catch (Exception e) {
            Log.w(LOG_TAG, "ConsoleService query failed, defaulting to UNKNOWN: " + e.getMessage());
            return MachineClass.UNKNOWN;
        } finally {
            if (channel != null) channel.shutdownNow();
        }
    }

    private static MachineClass toMachineClass(MachineType type) {
        switch (type) {
            case TREADMILL:
            case INCLINE_TRAINER:
                return MachineClass.TREADMILL;
            case BIKE:
            case SPIN_BIKE:
            case ELLIPTICAL:
            case VERTICAL_ELLIPTICAL:
            case STRIDER:
            case FREE_STRIDER:
            case ROWER:
                return MachineClass.BIKE;
            default:
                return MachineClass.UNKNOWN;
        }
    }

    public TelemetryReader createTelemetryReader(Context context) {
        return new GrpcTelemetryReader(context.getApplicationContext());
    }

    public Device createDevice(Context context) {
        GrpcCommandTransport transport = new GrpcCommandTransport(context);
        return machineClass == MachineClass.TREADMILL
                ? new GrpcTreadmillDevice(transport)
                : new GrpcBikeDevice(transport);
    }

    private static ClientInterceptor clientIdInterceptor() {
        return new ClientInterceptor() {
            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                    MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
                return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                        next.newCall(method, callOptions)) {
                    @Override
                    public void start(Listener<RespT> responseListener, Metadata headers) {
                        headers.put(CLIENT_ID_HEADER, GrpcCredentials.CLIENT_ID_HEADER_VALUE);
                        super.start(responseListener, headers);
                    }
                };
            }
        };
    }
}
