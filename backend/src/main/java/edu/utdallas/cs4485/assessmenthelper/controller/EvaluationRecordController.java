package edu.utdallas.cs4485.assessmenthelper.controller;

import edu.utdallas.cs4485.assessmenthelper.service.EvaluationRecord;
import edu.utdallas.cs4485.assessmenthelper.service.EvaluationRecordService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/professors/{professorId}/evaluations")
public class EvaluationRecordController {

    private final EvaluationRecordService service;

    public EvaluationRecordController(EvaluationRecordService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@professorAccess.canView(principal, #professorId)")
    public List<EvaluationRecord> list(@PathVariable Integer professorId) {
        return service.findByProfessor(professorId);
    }
}
