package edu.utdallas.cs4485.assessmenthelper.controller;

import edu.utdallas.cs4485.assessmenthelper.service.ObserverCandidate;
import edu.utdallas.cs4485.assessmenthelper.service.ObserverCandidateService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/observer-candidates")
public class ObserverCandidateController {

    private final ObserverCandidateService service;

    public ObserverCandidateController(ObserverCandidateService service) {
        this.service = service;
    }

    @GetMapping
    public List<ObserverCandidate> list(
            @RequestParam String courseNumber, @RequestParam String sectionNumber, @RequestParam String professorName) {
        return service.findCandidates(courseNumber, sectionNumber, professorName);
    }
}
