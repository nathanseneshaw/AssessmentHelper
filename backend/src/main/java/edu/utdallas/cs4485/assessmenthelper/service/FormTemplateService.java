package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.entity.ObservationCriterion;
import edu.utdallas.cs4485.assessmenthelper.entity.ObservationTemplate;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationCriterionRepository;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationTemplateRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FormTemplateService {
    public record Criterion(String title, boolean selected, boolean notes) {}

    public record Template(int templateId, int version, List<Criterion> criteria) {}

    public record Update(Integer templateId, List<Criterion> criteria) {}

    private final ObservationTemplateRepository templates;
    private final ObservationCriterionRepository criterionRepository;
    private final Clock clock;

    public FormTemplateService(
            ObservationTemplateRepository templates, ObservationCriterionRepository criterionRepository, Clock clock) {
        this.templates = templates;
        this.criterionRepository = criterionRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Template current() {
        var template = templates
                .findFirstByActiveStatusTrueOrderByVersionDescTemplateIdDesc()
                .orElseThrow(() -> new ConflictException("Configure an active observation template first."));
        return version(template.templateId());
    }

    private Template version(int id) {
        var template = templates.findById(id).orElseThrow(() -> new NotFoundException("Template version not found."));
        var criteria = criterionRepository.findByTemplateIdOrderByDisplayOrderAscCriterionIdAsc(id).stream()
                .map(c -> new Criterion(c.criterionText(), c.selected(), c.notesEnabled()))
                .toList();
        return new Template(id, template.version(), criteria);
    }

    @Transactional(readOnly = true)
    public List<Template> history() {
        return templates.findTemplateIdsNewestFirst().stream()
                .map(this::version)
                .toList();
    }

    @Transactional
    public Template restore(int sourceId, Integer currentId) {
        if (currentId == null) throw new IllegalArgumentException("The current template ID is required.");
        lockCurrent(currentId);
        if (sourceId == currentId) throw new ConflictException("This version is already current.");
        version(sourceId);
        templates.deactivateActiveTemplates();
        templates.activateTemplate(sourceId);
        return current();
    }

    private void lockCurrent(Integer expectedId) {
        // Every save and restore locks the same retained row, even when the active version moves backwards.
        var locked = templates.lockFirstTemplateId();
        if (locked.isEmpty() || current().templateId() != expectedId)
            throw new ConflictException(
                    "Another committee member updated this template. Close and reopen this window to load their changes.");
    }

    @Transactional
    public Template save(Update update, Integer userId) {
        if (update == null || update.templateId() == null || update.criteria() == null)
            throw new IllegalArgumentException("A template and criteria are required.");
        if (update.criteria().isEmpty() || update.criteria().size() > 100)
            throw new IllegalArgumentException("Provide between 1 and 100 criteria.");
        var titles = new HashSet<String>();
        for (var criterion : update.criteria()) {
            if (criterion == null
                    || criterion.title() == null
                    || criterion.title().isBlank()
                    || criterion.title().trim().length() > 500)
                throw new IllegalArgumentException("Each criterion needs a name of up to 500 characters.");
            if (!titles.add(criterion.title().trim().toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("Criterion names must be unique.");
        }
        if (update.criteria().stream().noneMatch(Criterion::selected))
            throw new IllegalArgumentException("Select at least one criterion for the observation form.");
        lockCurrent(update.templateId());
        var old = templates.findById(update.templateId()).orElseThrow();
        templates.deactivateActiveTemplates();
        int nextVersion = templates.nextTemplateVersion();
        var saved = templates.save(
                new ObservationTemplate(null, userId, old.templateName(), nextVersion, LocalDate.now(clock), true));
        var criteria = new ArrayList<ObservationCriterion>();
        for (var criterion : update.criteria()) {
            criteria.add(new ObservationCriterion(
                    null,
                    saved.templateId(),
                    criterion.title().trim(),
                    "RATING",
                    true,
                    criteria.size() + 1,
                    1,
                    5,
                    criterion.selected(),
                    criterion.notes()));
        }
        criterionRepository.saveAll(criteria);
        return current();
    }
}
