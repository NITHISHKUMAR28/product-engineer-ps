package com.nithish.remainder.service;

import java.time.Instant;
import java.time.LocalDateTime;

public interface TimeResolver {

    Instant resolve(LocalDateTime localDateTime, String zone);
}