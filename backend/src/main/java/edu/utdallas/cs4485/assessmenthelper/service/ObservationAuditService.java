package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.entity.ObservationAuditLog;
import edu.utdallas.cs4485.assessmenthelper.entity.Observee;
import edu.utdallas.cs4485.assessmenthelper.repository.EvaluationSignupRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationAuditLogRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObserveeRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ProfessorRepository;
import edu.utdallas.cs4485.assessmenthelper.security.AccountPrincipal;
import edu.utdallas.cs4485.assessmenthelper.security.ProfessorAccess;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ObservationAuditService {

    public record AuditEntry(
            ObservationService.AuditEvent event, String actorName, String recipientName, LocalDateTime occurredAt) {}

    private final ObservationRepository observationRepository;
    private final EvaluationSignupRepository signupRepository;
    private final ObserveeRepository observeeRepository;
    private final ProfessorRepository professorRepository;
    private final ObservationAuditLogRepository auditLogRepository;
    private final ProfessorAccess professorAccess;

    public ObservationAuditService(
            ObservationRepository observationRepository,
            EvaluationSignupRepository signupRepository,
            ObserveeRepository observeeRepository,
            ProfessorRepository professorRepository,
            ObservationAuditLogRepository auditLogRepository,
            ProfessorAccess professorAccess) {
        this.observationRepository = observationRepository;
        this.signupRepository = signupRepository;
        this.observeeRepository = observeeRepository;
        this.professorRepository = professorRepository;
        this.auditLogRepository = auditLogRepository;
        this.professorAccess = professorAccess;
    }

    // The log holds no evaluation content, so unlike the record itself the committee may read it at any status.
    public List<AuditEntry> auditLog(int observationId, AccountPrincipal principal) {
        Integer observeeProfessorId = observationRepository
                .findById(observationId)
                .flatMap(observation -> signupRepository.findById(observation.signupId()))
                .flatMap(signup -> observeeRepository.findById(signup.observeeId()))
                .map(Observee::professorId)
                .orElseThrow(() -> new NotFoundException("Observation not found."));
        if (!principal.isCommitteeMember() || !professorAccess.canView(principal, observeeProfessorId))
            throw new AccessDeniedException("You do not have access to this audit log.");

        List<ObservationAuditLog> entries = auditLogRepository.findByObservationIdOrderByLogIdAsc(observationId);
        Map<Integer, String> names = new HashMap<>();
        professorRepository
                .findAllById(entries.stream()
                        .flatMap(e -> Stream.of(e.actorProfessorId(), e.recipientProfessorId()))
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .forEach(p -> names.put(p.professorId(), p.firstName() + " " + p.lastName()));
        return entries.stream()
                .map(e -> new AuditEntry(
                        ObservationService.AuditEvent.valueOf(e.eventType()),
                        names.get(e.actorProfessorId()),
                        names.get(e.recipientProfessorId()),
                        e.occurredAt()))
                .toList();
    }
}
