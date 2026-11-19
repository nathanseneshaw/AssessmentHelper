package edu.utdallas.cs4485.assessmenthelper.config;

import edu.utdallas.cs4485.assessmenthelper.service.DemoTime;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    @Bean
    Clock clock(ObjectProvider<DemoTime> demoTime) {
        Clock system = Clock.system(ZoneId.of("America/Chicago"));
        DemoTime time = demoTime.getIfAvailable();
        return time == null ? system : time.shift(system);
    }
}
