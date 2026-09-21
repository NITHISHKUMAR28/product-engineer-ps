package com.nithish.remainder.controller;

import com.nithish.remainder.config.MutableClock;
import com.nithish.remainder.service.BenchmarkService;
import com.nithish.remainder.service.SchedulerService;
import com.nithish.remainder.serviceImpl.FakeNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class BenchmarkController {

    private final BenchmarkService benchmarkService;
    private final SchedulerService schedulerService;
    private final MutableClock mutableClock;
    private final FakeNotifier fakeNotifier;

    @PostMapping("/simulate-failure/temporary")
    public ResponseEntity<Map<String, Object>> simulateTempFailure(@RequestParam(defaultValue = "1") int count) {
        fakeNotifier.simulateTemporaryFailures(count);
        return ResponseEntity.ok(Map.of("simulatedTemporaryFailures", count, "status", "configured"));
    }

    @PostMapping("/simulate-failure/reset")
    public ResponseEntity<Map<String, String>> resetFailures() {
        fakeNotifier.reset();
        return ResponseEntity.ok(Map.of("status", "reset"));
    }


    @PostMapping("/benchmark/run")
    public ResponseEntity<BenchmarkService.BenchmarkResult> runBenchmark() {
        return ResponseEntity.ok(benchmarkService.runBenchmark());
    }

    @PostMapping("/tick")
    public ResponseEntity<Map<String, Object>> tick() {
        int processed = schedulerService.tick();
        return ResponseEntity.ok(Map.of(
                "processedItems", processed,
                "currentClock", mutableClock.instant().toString()
        ));
    }

    @GetMapping("/clock")
    public ResponseEntity<Map<String, String>> getClock() {
        return ResponseEntity.ok(Map.of("instant", mutableClock.instant().toString()));
    }

    @PostMapping("/clock/advance")
    public ResponseEntity<Map<String, String>> advanceClock(
            @RequestParam(defaultValue = "0") long seconds,
            @RequestParam(defaultValue = "0") long minutes
    ) {
        mutableClock.advance(Duration.ofSeconds(seconds).plus(Duration.ofMinutes(minutes)));
        return ResponseEntity.ok(Map.of("instant", mutableClock.instant().toString()));
    }

    @PostMapping("/clock/set")
    public ResponseEntity<Map<String, String>> setClock(@RequestParam String isoInstant) {
        mutableClock.setInstant(Instant.parse(isoInstant));
        return ResponseEntity.ok(Map.of("instant", mutableClock.instant().toString()));
    }
}
