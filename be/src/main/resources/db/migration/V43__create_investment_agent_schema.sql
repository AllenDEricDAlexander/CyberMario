-- V43: create audited investment agent runs and durable paper-trade decision recovery state.

ALTER TABLE investment_research_report
    ADD CONSTRAINT uk_investment_research_report_workspace_id UNIQUE (id, workspace_id);

CREATE TABLE investment_agent_run (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    account_id BIGINT,
    agent_preset_code VARCHAR(128) NOT NULL,
    generic_agent_run_audit_id BIGINT NOT NULL,
    run_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    data_as_of TIMESTAMP WITH TIME ZONE NOT NULL,
    input_snapshot_json JSONB NOT NULL DEFAULT '{}',
    report_id BIGINT,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,
    error_code VARCHAR(256),
    error_message TEXT,
    idempotency_key VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_investment_agent_run_idempotency UNIQUE (idempotency_key),
    CONSTRAINT uk_investment_agent_run_generic_audit UNIQUE (generic_agent_run_audit_id),
    CONSTRAINT fk_investment_agent_run_workspace FOREIGN KEY (workspace_id)
        REFERENCES investment_workspace (id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_agent_run_account_workspace
        FOREIGN KEY (account_id, workspace_id)
        REFERENCES investment_paper_account (id, workspace_id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_agent_run_generic_audit FOREIGN KEY (generic_agent_run_audit_id)
        REFERENCES agent_run_audit (id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_agent_run_report_workspace
        FOREIGN KEY (report_id, workspace_id)
        REFERENCES investment_research_report (id, workspace_id) ON DELETE RESTRICT,
    CONSTRAINT chk_investment_agent_run_state CHECK (
        CHAR_LENGTH(agent_preset_code) > 0
        AND CHAR_LENGTH(idempotency_key) > 0
        AND run_type IN ('MARKET_ANALYSIS', 'INSTRUMENT_ANALYSIS', 'STRATEGY_REVIEW',
            'PORTFOLIO_REVIEW', 'AUTO_TRADE')
        AND status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')
        AND (run_type <> 'AUTO_TRADE' OR account_id IS NOT NULL)
        AND data_as_of <= started_at
        AND (
            (status IN ('PENDING', 'RUNNING') AND finished_at IS NULL)
            OR (status IN ('SUCCEEDED', 'FAILED') AND finished_at IS NOT NULL
                AND finished_at >= started_at)
        )
        AND (
            (status = 'FAILED' AND error_code IS NOT NULL AND CHAR_LENGTH(error_code) > 0)
            OR (status <> 'FAILED' AND error_code IS NULL AND error_message IS NULL)
        )
    )
);

CREATE INDEX idx_investment_agent_run_workspace_created
    ON investment_agent_run (workspace_id, created_at DESC);

CREATE INDEX idx_investment_agent_run_account_created
    ON investment_agent_run (account_id, created_at DESC);

CREATE INDEX idx_investment_agent_run_status_started
    ON investment_agent_run (status, started_at);

CREATE TABLE investment_agent_decision (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL,
    instrument_id BIGINT,
    action VARCHAR(32) NOT NULL,
    confidence NUMERIC(24, 12) NOT NULL,
    horizon VARCHAR(64) NOT NULL,
    thesis TEXT NOT NULL,
    risks_json JSONB NOT NULL DEFAULT '[]',
    invalidation_json JSONB NOT NULL DEFAULT '[]',
    requested_quantity NUMERIC(38, 18),
    requested_notional NUMERIC(38, 18),
    requested_leverage NUMERIC(24, 12),
    order_type VARCHAR(16),
    limit_price NUMERIC(38, 18),
    intent_id BIGINT,
    execution_status VARCHAR(32) NOT NULL,
    execution_idempotency_key VARCHAR(128),
    data_as_of TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_investment_agent_decision_intent UNIQUE (intent_id),
    CONSTRAINT uk_investment_agent_decision_execution UNIQUE (execution_idempotency_key),
    CONSTRAINT fk_investment_agent_decision_run FOREIGN KEY (run_id)
        REFERENCES investment_agent_run (id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_agent_decision_instrument FOREIGN KEY (instrument_id)
        REFERENCES investment_instrument (id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_agent_decision_intent FOREIGN KEY (intent_id)
        REFERENCES investment_trade_intent (id) ON DELETE RESTRICT,
    CONSTRAINT chk_investment_agent_decision_values CHECK (
        action IN ('HOLD', 'OPEN_LONG', 'OPEN_SHORT', 'CLOSE', 'REDUCE')
        AND confidence >= 0
        AND confidence <= 1
        AND CHAR_LENGTH(horizon) > 0
        AND CHAR_LENGTH(thesis) > 0
        AND status = 'VALIDATED'
        AND data_as_of <= created_at
        AND (expires_at IS NULL OR expires_at > data_as_of)
        AND (
            (action = 'HOLD'
                AND requested_quantity IS NULL
                AND requested_notional IS NULL
                AND requested_leverage IS NULL
                AND order_type IS NULL
                AND limit_price IS NULL)
            OR (action <> 'HOLD'
                AND instrument_id IS NOT NULL
                AND requested_quantity > 0
                AND requested_notional > 0
                AND requested_leverage > 0
                AND order_type IN ('MARKET', 'LIMIT')
                AND ((order_type = 'MARKET' AND limit_price IS NULL)
                    OR (order_type = 'LIMIT' AND limit_price > 0)))
        )
    ),
    CONSTRAINT chk_investment_agent_decision_execution CHECK (
        execution_status IN ('NOT_APPLICABLE', 'PENDING', 'SUBMITTED', 'FAILED')
        AND (
            (action = 'HOLD'
                AND execution_status = 'NOT_APPLICABLE'
                AND execution_idempotency_key IS NULL
                AND intent_id IS NULL)
            OR (action <> 'HOLD'
                AND execution_status IN ('PENDING', 'SUBMITTED', 'FAILED')
                AND execution_idempotency_key IS NOT NULL
                AND CHAR_LENGTH(execution_idempotency_key) > 0
                AND ((execution_status = 'SUBMITTED' AND intent_id IS NOT NULL)
                    OR (execution_status IN ('PENDING', 'FAILED') AND intent_id IS NULL)))
        )
    )
);

CREATE INDEX idx_investment_agent_decision_run
    ON investment_agent_decision (run_id, id);

CREATE INDEX idx_investment_agent_decision_instrument_as_of
    ON investment_agent_decision (instrument_id, data_as_of DESC);

CREATE INDEX idx_investment_agent_decision_execution_recovery
    ON investment_agent_decision (execution_status, intent_id, id);
