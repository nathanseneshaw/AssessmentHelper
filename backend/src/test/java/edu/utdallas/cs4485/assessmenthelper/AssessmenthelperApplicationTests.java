package edu.utdallas.cs4485.assessmenthelper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

// Every test loads data.sql, the app's own seed data. This one checks that it loads at all.
@SpringBootTest
class AssessmenthelperApplicationTests {

    @Autowired
    private JdbcTemplate db;

    @Test
    void contextLoadsWithTheAppSeedData() {
        assertThat(db.queryForObject("SELECT COUNT(*) FROM professor", Integer.class))
                .isPositive();
    }
}
