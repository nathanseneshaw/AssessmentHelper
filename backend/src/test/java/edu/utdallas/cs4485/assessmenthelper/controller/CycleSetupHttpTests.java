package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "cycle.http.test=true")
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "AC")
class CycleSetupHttpTests {
    @Autowired
    MockMvc mvc;

    @MockitoBean
    Clock clock;

    @Test
    void multipartSetupCreatesAnUpcomingCycle() throws Exception {
        doReturn(Instant.parse("2026-11-19T12:00:00Z")).when(clock).instant();
        doReturn(ZoneOffset.UTC).when(clock).getZone();
        var details =
                new MockMultipartFile("setup", "setup.json", "application/json", """
                {"term":"Spring","year":2027,"startDate":"2027-01-19","endDate":"2027-05-14",
                 "signupDeadline":"2027-02-10","observationDeadline":"2027-04-20","feedbackDeadline":"2027-05-10"}
                """.getBytes(StandardCharsets.UTF_8));
        var professors = new MockMultipartFile(
                "professors",
                "professors.csv",
                "text/csv",
                ("email,first_name,last_name,department_code,rank,hire_date,active\n"
                                + "http.setup@example.edu,Http,Setup,CS,ASSISTANT,2024-08-01,true\n")
                        .getBytes(StandardCharsets.UTF_8));
        var sections = new MockMultipartFile(
                "sections",
                "sections.csv",
                "text/csv",
                ("course_prefix,course_number,course_name,course_level,department_code,"
                                + "term,year,section,days,start_time,end_time,location,instructor_email\n"
                                + "CS,4390,Networks,,,Spring,2027,001,TR,10:00,11:15,,http.setup@example.edu\n")
                        .getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/ac/cycles")
                        .file(details)
                        .file(professors)
                        .file(sections)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cycleId").isNumber());
        mvc.perform(get("/api/ac/cycles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.term == 'Spring' && @.year == 2027)].period", hasItem("UPCOMING")));
    }

    @Test
    void failedUploadReturnsTheExistingRowAndColumnErrors() throws Exception {
        doReturn(Instant.parse("2026-11-19T12:00:00Z")).when(clock).instant();
        doReturn(ZoneOffset.UTC).when(clock).getZone();
        var details =
                new MockMultipartFile("setup", "setup.json", "application/json", """
                {"term":"Spring","year":2027,"startDate":"2027-01-19","endDate":"2027-05-14",
                 "signupDeadline":"2027-02-10","observationDeadline":"2027-04-20","feedbackDeadline":"2027-05-10"}
                """.getBytes(StandardCharsets.UTF_8));
        var professors = new MockMultipartFile(
                "professors",
                "professors.csv",
                "text/csv",
                ("email,first_name,last_name,department_code,rank,hire_date,active\n"
                                + "http.error@example.edu,Http,Error,CS,ASSISTANT,2024-08-01,true\n")
                        .getBytes(StandardCharsets.UTF_8));
        var sections = new MockMultipartFile(
                "sections",
                "sections.csv",
                "text/csv",
                ("course_prefix,course_number,course_name,course_level,department_code,"
                                + "term,year,section,days,start_time,end_time,location,instructor_email\n"
                                + "CS,4390,Networks,,,Spring,2027,001,TR,invalid,11:15,,http.error@example.edu\n")
                        .getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/ac/cycles")
                        .file(details)
                        .file(professors)
                        .file(sections)
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fileKind").value("sections"))
                .andExpect(jsonPath("$.errors[0].row").value(2))
                .andExpect(jsonPath("$.errors[0].column").value("start_time"));
    }
}
