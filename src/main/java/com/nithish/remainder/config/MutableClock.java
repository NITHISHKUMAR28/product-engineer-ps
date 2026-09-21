package com.nithish.remainder.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

public class MutableClock extends Clock {

    private final AtomicReference<Instant> currentInstant;
    private final ZoneId zone;

    public MutableClock(Instant initialInstant, ZoneId zone) {
        this.currentInstant = new AtomicReference<>(initialInstant);
        this.zone = zone;
    }

    public MutableClock(Instant initialInstant) {
        this(initialInstant, ZoneOffset.UTC);
    }

    public MutableClock() {
        this(Instant.now(), ZoneOffset.UTC);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(currentInstant.get(), zone);
    }

    @Override
    public Instant instant() {
        return currentInstant.get();
    }

    public void advance(Duration duration) {
        currentInstant.updateAndGet(current -> current.plus(duration));
    }

    public void setInstant(Instant newInstant) {
        currentInstant.set(newInstant);
    }

    public void reset() {
        currentInstant.set(Instant.now());
    }
}
