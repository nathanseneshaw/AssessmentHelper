package edu.utdallas.cs4485.assessmenthelper.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One shared clock so that deadline and expiration logic can be tested with a fixed time. */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
