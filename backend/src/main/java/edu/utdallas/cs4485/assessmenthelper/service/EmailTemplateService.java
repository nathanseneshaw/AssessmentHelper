package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.entity.MessageTemplate;
import edu.utdallas.cs4485.assessmenthelper.repository.MessageTemplateRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Plain-text email templates. Rendering never evaluates expressions or HTML. */
@Service
public class EmailTemplateService {
    public static final List<String> PLACEHOLDERS = List.of(
            "professorName",
            "lastName",
            "cycleLabel",
            "rankDescription",
            "interval",
            "evaluationHistory",
            "signupDeadline",
            "observationDeadline",
            "courseName");
    private static final Pattern VARIABLE = Pattern.compile("[{][{]([a-zA-Z][a-zA-Z0-9]*)[}][}]");
    private final MessageTemplateRepository repository;

    public EmailTemplateService(MessageTemplateRepository repository) {
        this.repository = repository;
    }

    public record Catalog(List<MessageTemplate> templates, List<String> placeholders) {}

    public record Update(String name, String subject, String body, Boolean activeStatus, Long version) {}

    public record Preview(String subject, String body) {}

    @Transactional(readOnly = true)
    public Catalog catalog() {
        return new Catalog(
                repository.findAll().stream()
                        .sorted(Comparator.comparing(MessageTemplate::templateType))
                        .toList(),
                PLACEHOLDERS);
    }

    @Transactional
    public MessageTemplate save(String type, Update update) {
        if (type == null || !type.matches("[A-Z][A-Z0-9_]{0,29}"))
            throw new IllegalArgumentException("Use an uppercase template key of up to 30 characters.");
        if (update == null
                || update.name() == null
                || update.name().isBlank()
                || update.name().length() > 200
                || update.activeStatus() == null)
            throw new IllegalArgumentException("Provide a name (up to 200 characters) and active status.");
        validateText(update.subject(), update.body());
        var existing = repository.findByTemplateType(type).orElse(null);
        if (existing != null && !java.util.Objects.equals(existing.version(), update.version()))
            throw new ConflictException("This template changed. Reload it before saving again.");
        if (existing == null && update.version() != null) throw new NotFoundException("Email template not found.");
        try {
            return repository.save(new MessageTemplate(
                    existing == null ? null : existing.templateId(),
                    update.name().trim(),
                    update.subject(),
                    update.body(),
                    type,
                    update.activeStatus(),
                    update.version()));
        } catch (OptimisticLockingFailureException | DuplicateKeyException e) {
            throw new ConflictException("This template changed. Reload it before saving again.");
        }
    }

    public Preview preview(Update draft) {
        if (draft == null) throw new IllegalArgumentException("Provide a template to preview.");
        validateText(draft.subject(), draft.body());
        return renderText(
                draft.subject(),
                draft.body(),
                Map.of(
                        "professorName",
                        "Alex Nguyen",
                        "lastName",
                        "Nguyen",
                        "cycleLabel",
                        "Fall 2026",
                        "rankDescription",
                        "an Associate Professor",
                        "interval",
                        "2 years",
                        "evaluationHistory",
                        "Your last evaluation was completed in Fall 2024.",
                        "signupDeadline",
                        "2026-10-31",
                        "observationDeadline",
                        "2026-11-15",
                        "courseName",
                        "CS 3345.001"));
    }

    @Transactional(readOnly = true)
    public Preview render(String type, Map<String, String> context) {
        var template = repository
                .findByTemplateType(type)
                .orElseThrow(() -> new NotFoundException("Email template not found: " + type));
        if (!Boolean.TRUE.equals(template.activeStatus()))
            throw new ConflictException("This email template is inactive.");
        validateText(template.subject(), template.body());
        return renderText(template.subject(), template.body(), context);
    }

    private static void validateText(String subject, String body) {
        if (subject == null
                || subject.isBlank()
                || subject.length() > 255
                || subject.contains("\n")
                || subject.contains("\r")
                || body == null
                || body.isBlank()
                || body.length() > 16000)
            throw new IllegalArgumentException(
                    "Provide a single-line subject (up to 255 characters) and message (up to 16000 characters).");
        for (String text : List.of(subject, body)) {
            var matcher = VARIABLE.matcher(text);
            while (matcher.find())
                if (!PLACEHOLDERS.contains(matcher.group(1)))
                    throw new IllegalArgumentException("Unknown placeholder: " + matcher.group(1));
            String remainder = matcher.replaceAll("");
            if (remainder.contains("{{") || remainder.contains("}}"))
                throw new IllegalArgumentException("Write placeholders as {{lastName}} using the supported names.");
        }
    }

    private static Preview renderText(String subject, String body, Map<String, String> context) {
        var result = new Preview(replace(subject, context), replace(body, context));
        if (result.subject().length() > 255
                || result.subject().contains("\n")
                || result.subject().contains("\r")
                || result.body().length() > 16000)
            throw new IllegalArgumentException("The filled email exceeds the subject or message limits.");
        return result;
    }

    private static String replace(String text, Map<String, String> context) {
        return VARIABLE.matcher(text).replaceAll(match -> {
            String value = context.get(match.group(1));
            if (value == null || value.isBlank())
                throw new ConflictException("Missing email information: " + match.group(1));
            return java.util.regex.Matcher.quoteReplacement(value);
        });
    }
}
