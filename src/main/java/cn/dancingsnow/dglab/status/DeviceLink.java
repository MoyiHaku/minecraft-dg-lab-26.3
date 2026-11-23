package cn.dancingsnow.dglab.status;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the strength the HUD is showing into DG-LAB socket V3 messages for the paired app.
 * Pulses are queued on the app side; we keep our own frame counter so a burst of hits cannot
 * pile up more than {@link DamageOptions#queueSeconds} seconds of wave.
 */
final class DeviceLink {
    private static final int FRAME_MS = 100;
    private static final int FRAMES_PER_WAVE = 18;

    private final DamageOptions options;
    private final int[] queued = new int[3]; // index by protocol channel: 1 = A, 2 = B
    private ReadOnlySession session;
    private long drainedAt = System.nanoTime();

    DeviceLink(DamageOptions options) { this.options = options; }

    void session(ReadOnlySession next) {
        if (session == next) return;
        if (session != null) stop();
        session = next;
        drainedAt = System.nanoTime();
    }

    ReadOnlySession session() { return session; }

    boolean available() {
        ReadOnlySession current = session;
        return options.device && current != null
                && current.view().phase() == ReadOnlySession.Phase.CONNECTED;
    }

    /** True when the app still has room for one more wave; dropped events leave the HUD untouched. */
    boolean accept(String wave, int frames) {
        if (!available()) return true;
        for (int channel : channels())
            if (queued[channel] + frames > capacity()) return false;
        return true;
    }

    /** Queues the wave and matches the strength to the HUD. No clearing: the app plays them in order. */
    boolean play(String wave, int points) {
        if (!available()) return false;
        int frames = Math.max(1, frames(wave));
        boolean sent = strength(points);
        String payload = payload(wave);
        for (int channel : channels()) {
            if (queued[channel] + frames > capacity()) continue;
            queued[channel] += frames;
            sent |= session.send("pulse-" + (char) ('A' + channel - 1) + ":" + payload);
        }
        return sent;
    }

    /** Follows the HUD value, including the tick-by-tick fall back towards the base strength. */
    boolean strength(int points) {
        ReadOnlySession current = session;
        if (!available()) return false;
        Strength reading = current.view().strength();
        if (reading == null) return false;
        int wanted = Math.clamp(points, 0, 200);
        boolean sent = false;
        for (int channel : channels()) {
            int value = channel == 1 ? reading.a() : reading.b();
            int maximum = channel == 1 ? reading.aMax() : reading.bMax();
            // Without a reported limit the app is willing to take up to 100.
            int target = Math.min(maximum > 0 ? maximum : 100, wanted);
            int delta = target - value;
            if (delta != 0)
                sent |= current.send("strength-" + channel + (delta > 0 ? "+" : "-") + Math.abs(delta));
        }
        return sent;
    }

    /** Clears whatever is queued and lets both channels fall back to zero. */
    void stop() {
        for (int channel = 1; channel <= 2; channel++) queued[channel] = 0;
        drainedAt = System.nanoTime();
        ReadOnlySession current = session;
        if (current == null || current.view().phase() != ReadOnlySession.Phase.CONNECTED) return;
        for (int channel : channels()) current.send("clear-" + channel);
        Strength reading = current.view().strength();
        if (reading == null) return;
        for (int channel : channels()) {
            int value = channel == 1 ? reading.a() : reading.b();
            if (value > 0) current.send("strength-" + channel + "-" + value);
        }
    }

    /** One frame is consumed per 100 ms per channel; run every client tick. */
    void tick() {
        long now = System.nanoTime();
        long frames = (now - drainedAt) / (FRAME_MS * 1_000_000L);
        if (frames <= 0) return;
        drainedAt += frames * FRAME_MS * 1_000_000L;
        for (int channel = 1; channel <= 2; channel++)
            queued[channel] = (int) Math.max(0, queued[channel] - frames);
    }

    /** Frames this preset occupies with the current pulse-frame setting. */
    int frames(String wave) {
        return FRAMES_PER_WAVE * Math.clamp(options.deviceFrames, 1, 3);
    }

    /** Frames the app may hold in total, from the configured queue length in seconds. */
    private long capacity() {
        return (long) Math.max(0, options.queueSeconds) * (1000 / FRAME_MS);
    }

    /** Channel numbers used by the protocol: 1 = A, 2 = B. */
    private List<Integer> channels() {
        List<Integer> used = new ArrayList<>(2);
        if ((options.deviceChannel & 1) != 0) used.add(1);
        if ((options.deviceChannel & 2) != 0) used.add(2);
        if (used.isEmpty()) used.add(1);
        return used;
    }

    /**
     * One 100 ms frame carries eight amplitudes. Each thumbnail column is spread over
     * {@code deviceFrames} frames and interpolated, so the device ramps like the drawn bar.
     */
    private String payload(String wave) {
        List<Integer> columns = WavePreviews.ALL.getOrDefault(wave, WavePreviews.ALL.get(WavePreviews.DEFAULT));
        int perColumn = Math.clamp(options.deviceFrames, 1, 3);
        StringBuilder json = new StringBuilder("[");
        int previous = 0;
        boolean first = true;
        for (int column : columns) {
            int amplitude = Math.clamp(column * 100 / 6, 0, 100);
            for (int frame = 1; frame <= perColumn; frame++) {
                int value = previous + (amplitude - previous) * frame / perColumn;
                if (!first) json.append(',');
                first = false;
                json.append('"').append(frameHex(value)).append('"');
            }
            previous = amplitude;
        }
        return json.append(']').toString();
    }

    private static String frameHex(int amplitude) {
        StringBuilder hex = new StringBuilder(16);
        for (int i = 0; i < 8; i++) hex.append(Character.forDigit(amplitude >> 4 & 0xF, 16))
                .append(Character.forDigit(amplitude & 0xF, 16));
        return hex.toString();
    }
}
