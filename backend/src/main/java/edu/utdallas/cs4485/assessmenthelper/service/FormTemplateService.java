package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.entity.ObservationTemplate;
import edu.utdallas.cs4485.assessmenthelper.repository.ObservationTemplateRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FormTemplateService {
    public record Criterion(String title, boolean selected, boolean notes) {}

    public record Template(int templateId, int version, List<Criterion> criteria) {}

    public record Update(Integer templateId, List<Criterion> criteria) {}

    private final JdbcTemplate db;
    private final ObservationTemplateRepository templates;
    private final Clock clock;

    public FormTemplateService(JdbcTemplate db, ObservationTemplateRepository templates, Clock clock) {
        this.db = db;
        this.templates = templates;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Template current() {
        var ids = db.queryForList(
                "SELECT template_id FROM observation_template WHERE active_status=TRUE ORDER BY version DESC,template_id DESC",
                Integer.class);
        if (ids.isEmpty()) throw new ConflictException("Configure an active observation template first.");
        return version(ids.getFirst());
    }

    private Template version(int id) {
        var template = templates.findById(id).orElseThrow(() -> new NotFoundException("Template version not found."));
        var criteria = db.query(
                "SELECT criterion_text, selected, notes_enabled FROM observation_criterion WHERE template_id=? ORDER BY display_order,criterion_id",
                (r, n) -> new Criterion(r.getString(1), r.getBoolean(2), r.getBoolean(3)),
                id);
        return new Template(id, template.version(), criteria);
    }

    @Transactional(readOnly = true)
    public List<Template> history() {
        return db
                .queryForList(
                        "SELECT template_id FROM observation_template ORDER BY version DESC,template_id DESC",
                        Integer.class)
                .stream()
                .map(this::version)
                .toList();
    }

    @Transactional
    public Template restore(int sourceId, Integer currentId) {
        if (currentId == null) throw new IllegalArgumentException("The current template ID is required.");
        lockCurrent(currentId);
        if (sourceId == currentId) throw new ConflictException("This version is already current.");
        version(sourceId);
        db.update("UPDATE observation_template SET active_status=FALSE WHERE active_status=TRUE");
        db.update("UPDATE observation_template SET active_status=TRUE WHERE template_id=?", sourceId);
        return current();
    }

    private void lockCurrent(Integer expectedId) {
        // Every save and restore locks the same retained row, even when the active version moves backwards.
        var locked = db.queryForList(
                "SELECT template_id FROM observation_template WHERE template_id=(SELECT MIN(template_id) FROM observation_template) FOR UPDATE",
                Integer.class);
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
        db.update("UPDATE observation_template SET active_status=FALSE WHERE active_status=TRUE");
        int nextVersion =
                db.queryForObject("SELECT COALESCE(MAX(version),0)+1 FROM observation_template", Integer.class);
        var saved = templates.save(
                new ObservationTemplate(null, userId, old.templateName(), nextVersion, LocalDate.now(clock), true));
        int order = 0;
        for (var criterion : update.criteria()) {
            db.update(
                    "INSERT INTO observation_criterion(template_id,criterion_text,question_type,required,display_order,rating_min,rating_max,selected,notes_enabled) VALUES (?,?,'RATING',TRUE,?,1,5,?,?)",
                    saved.templateId(),
                    criterion.title().trim(),
                    ++order,
                    criterion.selected(),
                    criterion.notes());
        }
        return current();
    }
}
