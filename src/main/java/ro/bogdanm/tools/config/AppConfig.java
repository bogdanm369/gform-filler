package ro.bogdanm.tools.config;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public record AppConfig(
        Path configFile,
        HttpSettings httpSettings,
        List<FormJobConfig> jobs
) {

    public record HttpSettings(
            Duration connectTimeout,
            Duration readTimeout,
            String userAgent
    ) {
    }

    public record FormJobConfig(
            String name,
            URI link,
            Path questionFile,
            IntervalSeconds interval,
            boolean enabled,
            int fillCount
    ) {
        public boolean runsForever() {
            return fillCount <= 0;
        }
    }

    public record IntervalSeconds(
            int minSeconds,
            int maxSeconds
    ) {
        public IntervalSeconds {
            if (minSeconds <= 0 || maxSeconds <= 0) {
                throw new IllegalArgumentException("Interval seconds must be positive");
            }
            if (minSeconds > maxSeconds) {
                throw new IllegalArgumentException("Interval minimum cannot be greater than the maximum");
            }
        }

        public boolean isFixed() {
            return minSeconds == maxSeconds;
        }

        public int nextSeconds() {
            if (isFixed()) {
                return minSeconds;
            }
            return ThreadLocalRandom.current().nextInt(minSeconds, maxSeconds + 1);
        }

        public Duration nextDuration() {
            return Duration.ofSeconds(nextSeconds());
        }

        public String displayValue() {
            return isFixed() ? Integer.toString(minSeconds) : minSeconds + "-" + maxSeconds;
        }
    }
}

