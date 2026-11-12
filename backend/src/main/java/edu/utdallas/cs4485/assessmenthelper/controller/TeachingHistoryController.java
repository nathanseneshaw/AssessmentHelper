package edu.utdallas.cs4485.assessmenthelper.controller;

import edu.utdallas.cs4485.assessmenthelper.service.TeachingHistoryEntry;
import edu.utdallas.cs4485.assessmenthelper.service.TeachingHistoryService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/professors/{professorId}/teaching-history")
public class TeachingHistoryController {

    private final TeachingHistoryService service;

    public TeachingHistoryController(TeachingHistoryService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@professorAccess.canView(principal, #professorId)")
    public List<TeachingHistoryEntry> list(@PathVariable Integer professorId) {
        return service.findByProfessor(professorId);
    }
}
