package edu.utdallas.cs4485.assessmenthelper.controller;

import edu.utdallas.cs4485.assessmenthelper.entity.CourseSection;
import edu.utdallas.cs4485.assessmenthelper.service.ObserverAvailabilityService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/observer-availability")
public class ObserverAvailabilityController {

    /** {@code conflicts} lists the observer's own sections that clash with the signup's class. */
    public record Availability(
            Integer observerId, Integer signupId, boolean available, List<CourseSection> conflicts) {}

    private final ObserverAvailabilityService service;

    public ObserverAvailabilityController(ObserverAvailabilityService service) {
        this.service = service;
    }

    @GetMapping
    public Availability check(@RequestParam Integer signupId, @RequestParam Integer observerId) {
        List<CourseSection> conflicts = service.conflicts(observerId, signupId);
        return new Availability(observerId, signupId, conflicts.isEmpty(), conflicts);
    }
}
