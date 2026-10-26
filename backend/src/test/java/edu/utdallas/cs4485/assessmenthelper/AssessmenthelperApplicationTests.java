package edu.utdallas.cs4485.assessmenthelper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

// The other tests load test-data.sql, so this one checks that the app starts with its own seed data.
@SpringBootTest(properties = "spring.sql.init.data-locations=classpath:data.sql")
class AssessmenthelperApplicationTests {

    @Autowired
    private JdbcTemplate db;

    @Test
    void contextLoadsWithTheAppSeedData() {
        assertThat(db.queryForObject("SELECT COUNT(*) FROM professor", Integer.class))
                .isPositive();
    }
}
