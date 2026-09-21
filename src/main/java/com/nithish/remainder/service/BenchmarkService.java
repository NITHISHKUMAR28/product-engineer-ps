package com.nithish.remainder.service;

import java.util.Map;

public interface BenchmarkService {

    BenchmarkResult runBenchmark();

    record BenchmarkResult(
            int totalCreated,
            long deliveredCount,
            long cancelledCount,
            long failedCount,
            long totalNotificationsDelivered,
            long distinctDeliveryKeysInNotifications,
            boolean zeroDuplicateDeliveries,
            boolean allSettled,
            Map<String, Object> details
    ) {
    }
}
