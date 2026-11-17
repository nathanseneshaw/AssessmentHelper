package edu.utdallas.cs4485.assessmenthelper.controller;

import edu.utdallas.cs4485.assessmenthelper.service.CsvImportService;
import edu.utdallas.cs4485.assessmenthelper.service.ImportResult;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports")
@PreAuthorize("hasRole('AC')")
public class ImportController {

    private final CsvImportService service;

    public ImportController(CsvImportService service) {
        this.service = service;
    }

    @PostMapping(path = "/professors", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importProfessors(@RequestPart("file") MultipartFile file) throws IOException {
        return service.importProfessors(file.getBytes());
    }

    @PostMapping(path = "/sections", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importSections(@RequestPart("file") MultipartFile file) throws IOException {
        return service.importSections(file.getBytes());
    }

    @GetMapping("/professors/template")
    public ResponseEntity<String> professorsTemplate() {
        return template("professors", CsvImportService.professorsTemplate());
    }

    @GetMapping("/sections/template")
    public ResponseEntity<String> sectionsTemplate() {
        return template("sections", CsvImportService.sectionsTemplate());
    }

    private static ResponseEntity<String> template(String kind, String headerLine) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + kind + "-template.csv\"")
                .body(headerLine);
    }
}
