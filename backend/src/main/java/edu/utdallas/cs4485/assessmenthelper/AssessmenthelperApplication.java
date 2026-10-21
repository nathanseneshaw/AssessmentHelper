package edu.utdallas.cs4485.assessmenthelper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AssessmenthelperApplication {

    public static void main(String[] args) {
        SpringApplication.run(AssessmenthelperApplication.class, args);
    }
}
