package com.nithish.remainder.serviceImpl;

import com.nithish.remainder.service.TimeResolver;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.zone.ZoneRules;

@Service
public class TimeResolverImpl implements TimeResolver {

    @Override
    public Instant resolve(
            LocalDateTime localDateTime,
            String zone
    ) {
        ZoneId zoneId = ZoneId.of(zone);
        ZoneRules rules = zoneId.getRules();

        var validOffsets = rules.getValidOffsets(localDateTime);

        if (validOffsets.isEmpty()) {
            throw new IllegalArgumentException(
                    "The requested local time does not exist in zone: " + zone
            );
        }

        if (validOffsets.size() > 1) {
            throw new IllegalArgumentException(
                    "The requested local time is ambiguous in zone: " + zone
            );
        }

        return localDateTime
                .atOffset(validOffsets.get(0))
                .toInstant();
    }
}