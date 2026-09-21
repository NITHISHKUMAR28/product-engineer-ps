package com.nithish.remainder.exception;

public class StaleVersionException extends RuntimeException {

    public StaleVersionException(Long expected, Long actual) {
        super(
                "Stale reminder version. Expected: "
                        + expected
                        + ", actual: "
                        + actual
        );
    }
}
