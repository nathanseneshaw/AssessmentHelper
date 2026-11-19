package edu.utdallas.cs4485.assessmenthelper.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBooleanProperty("app.demo.enabled")
public class DemoTime {
    private final AtomicLong daysAhead = new AtomicLong();

    public long daysAhead() {
        return daysAhead.get();
    }

    void moveBy(long days) {
        daysAhead.addAndGet(days);
    }

    void reset() {
        daysAhead.set(0);
    }

    public Clock shift(Clock base) {
        return new Shifted(base);
    }

    private final class Shifted extends Clock {
        private final Clock base;

        Shifted(Clock base) {
            this.base = base;
        }

        @Override
        public ZoneId getZone() {
            return base.getZone();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new Shifted(base.withZone(zone));
        }

        @Override
        public Instant instant() {
            return base.instant()
                    .atZone(base.getZone())
                    .plusDays(daysAhead.get())
                    .toInstant();
        }
    }
}
