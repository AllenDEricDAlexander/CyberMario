-- V44: distinguish analysis-only recommendations from recoverable AUTO_TRADE execution work.

ALTER TABLE investment_agent_decision
    DROP CONSTRAINT chk_investment_agent_decision_execution;

ALTER TABLE investment_agent_decision
    ADD CONSTRAINT chk_investment_agent_decision_execution CHECK (
        execution_status IN ('NOT_APPLICABLE', 'PENDING', 'SUBMITTED', 'FAILED')
        AND (
            (execution_status = 'NOT_APPLICABLE'
                AND execution_idempotency_key IS NULL
                AND intent_id IS NULL)
            OR (action <> 'HOLD'
                AND execution_status IN ('PENDING', 'SUBMITTED', 'FAILED')
                AND execution_idempotency_key IS NOT NULL
                AND CHAR_LENGTH(execution_idempotency_key) > 0
                AND ((execution_status = 'SUBMITTED' AND intent_id IS NOT NULL)
                    OR (execution_status IN ('PENDING', 'FAILED') AND intent_id IS NULL)))
        )
        AND (action <> 'HOLD' OR execution_status = 'NOT_APPLICABLE')
    );
