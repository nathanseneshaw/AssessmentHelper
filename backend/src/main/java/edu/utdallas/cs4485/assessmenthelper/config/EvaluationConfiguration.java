package edu.utdallas.cs4485.assessmenthelper.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EvaluationConfiguration {
    @Bean
    Clock evaluationClock() {
        return Clock.system(ZoneId.of("America/Chicago"));
    }
}
