package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.repository.ObservationCriterionRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationTemplateRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The observation form: a template and its questions, in the order the form shows them. */
@Service
@Transactional(readOnly = true)
public class ObservationTemplateService {
    private final ObservationTemplateRepository templateRepository;
    private final ObservationCriterionRepository criterionRepository;

    public ObservationTemplateService(
            ObservationTemplateRepository templateRepository, ObservationCriterionRepository criterionRepository) {
        this.templateRepository = templateRepository;
        this.criterionRepository = criterionRepository;
    }

    /** One question. RATING questions take a whole number from ratingMin to ratingMax; TEXT questions take text. */
    public record Criterion(
            int criterionId,
            String criterionText,
            String questionType,
            boolean required,
            int displayOrder,
            Integer ratingMin,
            Integer ratingMax) {}

    public record Template(int templateId, String templateName, int version, List<Criterion> criteria) {}

    public Template template(int templateId) {
        return templateRepository
                .findById(templateId)
                .map(t -> new Template(
                        t.templateId(),
                        t.templateName(),
                        Objects.requireNonNullElse(t.version(), 0),
                        criteria(templateId)))
                .orElseThrow(() -> new NotFoundException("Observation template not found."));
    }

    private List<Criterion> criteria(int templateId) {
        return criterionRepository
                .findByTemplateIdAndSelectedTrueOrderByDisplayOrderAscCriterionIdAsc(templateId)
                .stream()
                .map(c -> new Criterion(
                        c.criterionId(),
                        c.criterionText(),
                        c.questionType(),
                        c.required(),
                        Objects.requireNonNullElse(c.displayOrder(), 0),
                        c.ratingMin(),
                        c.ratingMax()))
                .toList();
    }
}
