package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.entity.Professor;
import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.TeachingHistoryRepository;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "AC")
class ImportIntegrationTests {

    private static final String SABLE = "sable.windrow@example.edu";
    private static final String PROFESSORS = "email,first_name,last_name,department_code,rank,hire_date,active\r\n"
            + SABLE + ",Sable,Windrow,CS,ASSISTANT,2024-08-19,true";
    private static final String SECTIONS = "course_prefix,course_number,course_name,course_level,department_code,"
            + "term,year,section,days,start_time,end_time,location,instructor_email\r\n"
            + "CS,4390,Computer Networks,,,Fall,2026,001,TR,10:00,11:15,," + SABLE;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ProfessorRepository professors;

    @Autowired
    private TeachingHistoryRepository teachingHistory;

    @Test
    void professorsAreUploadedFirstAndThenTheSectionsTheyTeach() throws Exception {
        upload("/api/imports/professors", PROFESSORS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.errors").isEmpty());

        upload("/api/imports/sections", SECTIONS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.rowCount").value(1));

        Professor sable = professors.findByEmailIgnoreCase(SABLE).orElseThrow();
        assertThat(teachingHistory.findByProfessorId(sable.professorId())).hasSize(1);

        mvc.perform(get("/api/ac-dashboard/metrics")).andExpect(status().isOk());
        mvc.perform(get("/api/evaluation-eligibility"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$..email", hasItem(SABLE)));
    }

    @Test
    void sectionsCannotNameAProfessorWhoIsNotUploadedYet() throws Exception {
        upload("/api/imports/sections", SECTIONS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(false))
                .andExpect(jsonPath("$.errors[0].row").value(2))
                .andExpect(jsonPath("$.errors[0].column").value("instructor_email"));
    }

    private ResultActions upload(String path, String csv) throws Exception {
        Cookie token =
                mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return mvc.perform(multipart(path)
                .file(new MockMultipartFile("file", "upload.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                .cookie(token)
                .header("X-XSRF-TOKEN", token.getValue()));
    }
}
