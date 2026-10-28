package edu.utdallas.cs4485.assessmenthelper.service;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The observation form: a template and its questions, in the order the form shows them. */
@Service
@Transactional(readOnly = true)
public class ObservationTemplateService {
    private final JdbcTemplate db;

    public ObservationTemplateService(JdbcTemplate db) {
        this.db = db;
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
        return db
                .query(
                        "SELECT template_id, template_name, version FROM observation_template WHERE template_id=?",
                        (r, n) -> new Template(r.getInt(1), r.getString(2), r.getInt(3), criteria(templateId)),
                        templateId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Observation template not found."));
    }

    private List<Criterion> criteria(int templateId) {
        return db.query(
                """
                SELECT * FROM observation_criterion WHERE template_id=? AND selected=TRUE ORDER BY display_order, criterion_id
                """,
                (r, n) -> new Criterion(
                        r.getInt("criterion_id"),
                        r.getString("criterion_text"),
                        r.getString("question_type"),
                        r.getBoolean("required"),
                        r.getInt("display_order"),
                        r.getObject("rating_min", Integer.class),
                        r.getObject("rating_max", Integer.class)),
                templateId);
    }
}
