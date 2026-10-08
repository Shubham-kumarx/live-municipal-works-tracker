-- Phase 3B data-preserving complaint issue-type alignment.
-- PostgreSQL only. Run in a transaction with ON_ERROR_STOP enabled.
-- Existing complaint rows are preserved without reclassification.

ALTER TABLE complaints
    DROP CONSTRAINT IF EXISTS complaints_ai_predicted_issue_type_check;

ALTER TABLE complaints
    ADD CONSTRAINT complaints_ai_predicted_issue_type_check CHECK (
        ai_predicted_issue_type IS NULL OR ai_predicted_issue_type IN (
            'DOMESTIC_TRASH', 'ILLEGAL_PARKING', 'DAMAGED_SIGN', 'POTHOLE',
            'ROAD_CRACK', 'GARBAGE_ACCUMULATION', 'WATERLOGGING',
            'DAMAGED_STREETLIGHT', 'OPEN_MANHOLE', 'OTHER'
        )
    ) NOT VALID;

ALTER TABLE complaints
    VALIDATE CONSTRAINT complaints_ai_predicted_issue_type_check;

ALTER TABLE complaints
    DROP CONSTRAINT IF EXISTS complaints_final_issue_type_check;

ALTER TABLE complaints
    ADD CONSTRAINT complaints_final_issue_type_check CHECK (
        final_issue_type IN (
            'DOMESTIC_TRASH', 'ILLEGAL_PARKING', 'DAMAGED_SIGN', 'POTHOLE',
            'ROAD_CRACK', 'GARBAGE_ACCUMULATION', 'WATERLOGGING',
            'DAMAGED_STREETLIGHT', 'OPEN_MANHOLE', 'OTHER'
        )
    ) NOT VALID;

ALTER TABLE complaints
    VALIDATE CONSTRAINT complaints_final_issue_type_check;
