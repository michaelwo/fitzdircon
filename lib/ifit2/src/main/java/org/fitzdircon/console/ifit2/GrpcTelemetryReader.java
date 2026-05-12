package org.fitzdircon.console.ifit2;

import android.content.Context;
import android.util.Log;

import com.ifit.glassos.util.Empty;
import com.ifit.glassos.workout.HeartRateServiceGrpc;
import com.ifit.glassos.workout.InclineServiceGrpc;
import com.ifit.glassos.workout.ResistanceServiceGrpc;
import com.ifit.glassos.workout.RpmServiceGrpc;
import com.ifit.glassos.workout.SpeedServiceGrpc;
import com.ifit.glassos.workout.WattsServiceGrpc;
import com.ifit.glassos.workout.WorkoutServiceGrpc;
import com.ifit.glassos.workout.WorkoutState;
import com.ifit.glassos.workout.WorkoutStateMessage;

import org.fitzdircon.telemetry.CadenceTelemetry;
import org.fitzdircon.telemetry.HeartRateTelemetry;
import org.fitzdircon.telemetry.InclineTelemetry;
import org.fitzdircon.telemetry.ResistanceTelemetry;
import org.fitzdircon.telemetry.SpeedTelemetry;
import org.fitzdircon.telemetry.Telemetry;
import org.fitzdircon.telemetry.TelemetryReader;
import org.fitzdircon.telemetry.WattsTelemetry;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.stub.StreamObserver;
import io.grpc.okhttp.OkHttpChannelBuilder;

public final class GrpcTelemetryReader implements TelemetryReader {
    private static final String LOG_TAG = "FZ:Dispatch";
    private static final Metadata.Key<String> CLIENT_ID_HEADER =
            Metadata.Key.of("client_id", Metadata.ASCII_STRING_MARSHALLER);

    public static volatile Consumer<Exception> onError = e -> {};
    public static volatile Consumer<String> onLine = s -> {};

    private final Context context;
    private volatile Consumer<Telemetry> listener;
    private ManagedChannel channel;
    private boolean started;
    private final AtomicBoolean workoutActive = new AtomicBoolean(false);

    public GrpcTelemetryReader(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public synchronized void read() throws IOException {
        if (started) return;
        try {
            GrpcCredentials credentials = GrpcCredentials.load(context);
            channel = OkHttpChannelBuilder
                    .forAddress("localhost", 54321)
                    .overrideAuthority("localhost:54321")
                    .sslSocketFactory(credentials.sslContext().getSocketFactory())
                    .hostnameVerifier((hostname, session) -> true)
                    .intercept(clientIdInterceptor())
                    .build();

            Empty empty = Empty.newBuilder().build();

            safePoll(() -> {
                WorkoutStateMessage event = WorkoutServiceGrpc.newBlockingStub(channel)
                        .withDeadlineAfter(2, TimeUnit.SECONDS)
                        .getWorkoutState(Empty.newBuilder().build());
                if (event.getWorkoutState() == WorkoutState.WORKOUT_STATE_RUNNING) activateMetrics();
            });

            subscribeWorkoutState();

            started = true;
            Log.i(LOG_TAG, "gRPC telemetry reader started");
        } catch (Exception e) {
            Log.e(LOG_TAG, "gRPC telemetry reader start failed: " + e.getMessage());
            shutdown();
            IOException io = e instanceof IOException ? (IOException) e : new IOException(e);
            throw io;
        }
    }

    private void subscribeWorkoutState() {
        if (channel == null) return;
        WorkoutServiceGrpc.newStub(channel).workoutStateChanged(
                Empty.newBuilder().build(),
                new StreamObserver<WorkoutStateMessage>() {
                    @Override
                    public void onNext(WorkoutStateMessage event) {
                        Log.d(LOG_TAG, "workout state: " + event.getWorkoutState());
                        if (event.getWorkoutState() == WorkoutState.WORKOUT_STATE_RUNNING) activateMetrics();
                        else deactivateMetrics();
                    }

                    @Override
                    public void onError(Throwable t) {
                        Log.e(LOG_TAG, "gRPC workoutStateChanged error: " + t.getMessage());
                        onError.accept(t instanceof Exception ? (Exception) t : new Exception(t));
                        if (started && channel != null) {
                            Log.i(LOG_TAG, "gRPC workoutStateChanged resubscribing");
                            subscribeWorkoutState();
                        }
                    }

                    @Override
                    public void onCompleted() {}
                });
    }

    private void activateMetrics() {
        if (!workoutActive.compareAndSet(false, true)) return;
        Log.i(LOG_TAG, "gRPC metric streams starting");
        Empty empty = Empty.newBuilder().build();

        safePoll(() -> emit(new InclineTelemetry(
                (float) InclineServiceGrpc.newBlockingStub(channel)
                        .withDeadlineAfter(2, TimeUnit.SECONDS).getIncline(empty).getLastInclinePercent())));
        safePoll(() -> emit(new ResistanceTelemetry((float) ResistanceServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(2, TimeUnit.SECONDS).getResistance(empty).getLastResistance())));
        safePoll(() -> emit(new SpeedTelemetry((float) SpeedServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(2, TimeUnit.SECONDS).getSpeed(empty).getLastKph())));
        safePoll(() -> emit(new CadenceTelemetry((float) RpmServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(2, TimeUnit.SECONDS).getRpm(empty).getLastRpm())));
        safePoll(() -> emit(new WattsTelemetry((float) WattsServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(2, TimeUnit.SECONDS).getWatts(empty).getLastWatts())));
        safePoll(() -> emit(new HeartRateTelemetry((float) HeartRateServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(2, TimeUnit.SECONDS).getHeartRate(empty).getLastBpm())));

        subscribeIncline();
        subscribeResistance();
        subscribeSpeed();
        subscribeRpm();
        subscribeWatts();
        subscribeHeartRate();
    }

    private void subscribeIncline() {
        if (!workoutActive.get() || channel == null) return;
        InclineServiceGrpc.newStub(channel).inclineSubscription(
                Empty.newBuilder().build(),
                observer(v -> new InclineTelemetry((float) v.getLastInclinePercent()),
                        "incline", this::subscribeIncline));
    }

    private void subscribeResistance() {
        if (!workoutActive.get() || channel == null) return;
        ResistanceServiceGrpc.newStub(channel).resistanceSubscription(
                Empty.newBuilder().build(),
                observer(v -> new ResistanceTelemetry((float) v.getLastResistance()),
                        "resistance", this::subscribeResistance));
    }

    private void subscribeSpeed() {
        if (!workoutActive.get() || channel == null) return;
        SpeedServiceGrpc.newStub(channel).speedSubscription(
                Empty.newBuilder().build(),
                observer(v -> new SpeedTelemetry((float) v.getLastKph()),
                        "speed", this::subscribeSpeed));
    }

    private void subscribeRpm() {
        if (!workoutActive.get() || channel == null) return;
        RpmServiceGrpc.newStub(channel).rpmSubscription(
                Empty.newBuilder().build(),
                observer(v -> new CadenceTelemetry((float) v.getLastRpm()),
                        "rpm", this::subscribeRpm));
    }

    private void subscribeWatts() {
        if (!workoutActive.get() || channel == null) return;
        WattsServiceGrpc.newStub(channel).wattsSubscription(
                Empty.newBuilder().build(),
                observer(v -> new WattsTelemetry((float) v.getLastWatts()),
                        "watts", this::subscribeWatts));
    }

    private void subscribeHeartRate() {
        if (!workoutActive.get() || channel == null) return;
        HeartRateServiceGrpc.newStub(channel).heartRateSubscription(
                Empty.newBuilder().build(),
                observer(v -> new HeartRateTelemetry((float) v.getLastBpm()),
                        "heartRate", this::subscribeHeartRate));
    }

    private void deactivateMetrics() {
        workoutActive.set(false);
        Log.i(LOG_TAG, "gRPC metric streams inactive");
    }

    @Override
    public boolean subscribe(Consumer<Telemetry> listener) {
        this.listener = listener;
        return true;
    }

    private interface Mapper<T> {
        Telemetry map(T value);
    }

    private interface Poll {
        void run() throws Exception;
    }

    private static void safePoll(Poll poll) {
        try {
            poll.run();
        } catch (Exception e) {
            onError.accept(e);
        }
    }

    private <T> StreamObserver<T> observer(Mapper<T> mapper, String metric, Runnable resubscribe) {
        return new StreamObserver<T>() {
            @Override
            public void onNext(T value) {
                Telemetry telemetry = mapper.map(value);
                Log.d(LOG_TAG, "telemetry " + metric + "=" + telemetry.value);
                onLine.accept("glassos: " + metric);
                emit(telemetry);
            }

            @Override
            public void onError(Throwable t) {
                Log.e(LOG_TAG, "gRPC " + metric + " stream error: " + t.getMessage());
                onError.accept(t instanceof Exception ? (Exception) t : new Exception(t));
                if (workoutActive.get() && channel != null) {
                    Log.i(LOG_TAG, "gRPC " + metric + " resubscribing");
                    resubscribe.run();
                }
            }

            @Override
            public void onCompleted() {
            }
        };
    }

    private void emit(Telemetry telemetry) {
        Consumer<Telemetry> l = listener;
        if (l != null) l.accept(telemetry);
    }

    private synchronized void shutdown() {
        if (channel != null) {
            channel.shutdownNow();
            channel = null;
            Log.d(LOG_TAG, "gRPC channel shut down");
        }
    }

    private static ClientInterceptor clientIdInterceptor() {
        return new ClientInterceptor() {
            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                    MethodDescriptor<ReqT, RespT> method,
                    CallOptions callOptions,
                    Channel next) {
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
