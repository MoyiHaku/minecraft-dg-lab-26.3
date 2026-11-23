package com.lumoren.dglabcraft.hud;

import java.util.Arrays;

/** Health readings and a short history for drawing the player's HUD. */
final class FeedbackState {
    static final int HISTORY = 55;
    private final int[] samples = new int[HISTORY];
    private int next;
    private float previous = Float.NaN;
    private int hitTicks;
    private float lastLoss;
    private String lastHit = "hit";
    private int clock;
    private boolean low;

    void reset() {
        Arrays.fill(samples, 0);
        next = 0;
        previous = Float.NaN;
        hitTicks = 0;
        clock = 0;
        low = false;
        lastLoss = 0;
        lastHit = "hit";
    }

    void tick(float health, float maximum, String cause) {
        float hp = Math.clamp(health, 0f, maximum);
        float loss = Float.isNaN(previous) ? 0f : Math.max(0f, Math.min(previous, maximum) - hp);
        previous = hp;
        low = hp > 0 && hp <= 6;
        clock++;
        if (hitTicks > 0) hitTicks--;
        if (loss > 0.001f) {
            hitTicks = 45;
            lastLoss = loss;
            lastHit = cause;
        }
        int previousBar = samples[Math.floorMod(next - 1, HISTORY)];
        int damageBar = loss > 0 ? Math.clamp(Math.round(30 + loss * 8), 0, 100) : previousBar * 2 / 3;
        int phase = Math.floorMod(clock, 22);
        int heartbeatBar = low && (phase == 0 || phase == 4) ? (phase == 0 ? 65 : 32) : 0;
        samples[next] = Math.max(damageBar, heartbeatBar);
        next = (next + 1) % HISTORY;
    }

    boolean hasHit() { return hitTicks > 0; }
    boolean isLow() { return low; }
    float heartsLost() { return lastLoss / 2f; }
    String cause() { return lastHit; }
    int sampleFromEnd(int offset) { return samples[Math.floorMod(next - 1 - offset, HISTORY)]; }
}
