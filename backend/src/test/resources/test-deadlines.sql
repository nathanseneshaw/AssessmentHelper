-- Existing scenarios use a fixed Fall 2026 timeline independent of demo defaults.
UPDATE evaluation_cycle SET observation_deadline = DATE '2026-11-15', feedback_deadline = DATE '2026-12-01'
WHERE cycle_id = 1;
UPDATE observer_list_state SET window_end = DATE '2026-11-15'
WHERE signup_id IN (SELECT es.signup_id FROM evaluation_signup es
    JOIN observee o ON o.observee_id = es.observee_id WHERE o.cycle_id = 1)
    AND window_end = DATE '2026-12-10';
