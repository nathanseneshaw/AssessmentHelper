package edu.utdallas.cs4485.assessmenthelper.controller;

import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.utdallas.cs4485.assessmenthelper.config.SecurityConfig;
import edu.utdallas.cs4485.assessmenthelper.service.CsvImportService;
import edu.utdallas.cs4485.assessmenthelper.service.ImportResult;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ImportController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "AC")
class ImportControllerTests {

    private static final byte[] CSV =
            "email,first_name,last_name,department_code\r\nann.example@example.edu,Ann,Example,CS"
                    .getBytes(StandardCharsets.UTF_8);
    private static final MockMultipartFile FILE = new MockMultipartFile("file", "professors.csv", "text/csv", CSV);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CsvImportService service;

    @Test
    void theTemplateIsAHeaderOnlyCsvAttachment() throws Exception {
        mockMvc.perform(get("/api/imports/professors/template"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string(
                                HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"professors-template.csv\""))
                .andExpect(content().string("email,first_name,last_name,department_code,rank,hire_date,active\r\n"));
    }

    @Test
    void anUploadIsPassedToTheImportAndItsResultReturned() throws Exception {
        when(service.importSections(any())).thenReturn(new ImportResult(true, 1, List.of()));

        mockMvc.perform(multipart("/api/imports/sections").file(FILE).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.rowCount").value(1))
                .andExpect(jsonPath("$.errors").isEmpty());

        verify(service).importSections(aryEq(CSV));
    }

    @Test
    void aMissingFilePartIs400ProblemDetail() throws Exception {
        mockMvc.perform(multipart("/api/imports/professors").with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Required part 'file' is not present."));

        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "PROFESSOR")
    void onlyTheCommitteeCanImport() throws Exception {
        mockMvc.perform(multipart("/api/imports/professors").file(FILE).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/imports/professors/template")).andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }
}
