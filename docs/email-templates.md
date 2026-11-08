# Email templates and simulated delivery

AC members manage templates under **Email Templates** (/ac/email-templates). The initial catalog preserves the existing Due, Overdue, and both Follow Up messages. Notify and Follow Up load the saved server template with the recipient's current evaluation data. The sender can edit that individual draft without changing the shared template.

**Send Email** records the edited recipient, subject, body, cycle, professor and timestamp in evaluation_notification. The confirmation says **Email Recorded**. Nothing is delivered to a mailbox. Previewing, saving a template or cancelling a draft creates no notification and does not mark a professor notified. The existing recipient and eligibility checks still run when recording an email.

## Add another scenario

1. In Email Templates choose **New template**, enter a stable uppercase key such as OBSERVATION_REMINDER, a name, subject and message, then save.
2. Use supported placeholders in double braces, for example: Hello {{lastName}}, please submit {{courseName}} by {{observationDeadline}}. Preview uses clearly labelled sample data.
3. Connect that workflow's backend service to EmailTemplateService.render(key, context). Build context from trusted records, including the authenticated recipient and the relevant observation/cycle. Reuse the plain-text renderer and validate the workflow's authorization and eligibility before recording a notification. Creating a template alone does not trigger notifications.
4. Add default text to data.sql for clean installations and tests. Add any genuinely new placeholder name to EmailTemplateService.PLACEHOLDERS and its sample preview data, and supply it in the new workflow's context. Test the new recipient, deadline and transition rules.

Keys used by the current evaluation workflow:

| Situation | Key |
| --- | --- |
| Evaluation due | EVALUATION_DUE |
| Due follow-up | EVALUATION_DUE_FOLLOW_UP |
| Evaluation overdue | EVALUATION_OVERDUE |
| Overdue follow-up | EVALUATION_OVERDUE_FOLLOW_UP |

The evaluation workflow supports professorName, lastName, cycleLabel, rankDescription, interval, evaluationHistory, signupDeadline and observationDeadline. courseName is supported by the renderer for future observation workflows but is not supplied by the evaluation reminder workflow. Missing values, inactive templates, unknown placeholders and oversized filled messages fail explicitly instead of producing incomplete drafts. Template saves use optimistic versions to reject concurrent stale edits.

## API

All routes require an AC session. Mutations require CSRF protection.

- GET /api/email-templates: catalog and supported placeholders.
- PUT /api/email-templates/{key}: create with version=null or update with the loaded version; fields are name, subject, body, activeStatus, version.
- POST /api/email-templates/preview: draft subject and body with sample values; no save or notification.
- GET /api/{due|overdue}-evaluations/{cycleId}/professors/{professorId}/email-preview: current recipient-specific draft. Uses the follow-up template if this professor already has a notification in the cycle.
- POST /api/{due|overdue}-evaluations/{cycleId}/professors/{professorId}/notifications: existing simulated delivery route; edits apply only to this notification.

The development database is in-memory H2. Template edits and simulated notifications reset when the backend restarts. Persistent storage and SMTP delivery are separate future work.
