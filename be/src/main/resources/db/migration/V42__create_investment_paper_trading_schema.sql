-- V42: create isolated USDT futures paper-trading accounts, risk, order, and ledger schema.

CREATE TABLE investment_paper_account (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    margin_asset VARCHAR(32) NOT NULL,
    initial_equity NUMERIC(38, 18) NOT NULL,
    wallet_balance NUMERIC(38, 18) NOT NULL,
    ledger_sequence BIGINT NOT NULL DEFAULT 0,
    margin_mode VARCHAR(32) NOT NULL,
    position_mode VARCHAR(32) NOT NULL,
    trading_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    agent_auto_trade_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL,
    opened_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_investment_paper_account_workspace_name UNIQUE (workspace_id, name),
    CONSTRAINT uk_investment_paper_account_workspace_id UNIQUE (id, workspace_id),
    CONSTRAINT fk_investment_paper_account_workspace FOREIGN KEY (workspace_id)
        REFERENCES investment_workspace (id) ON DELETE RESTRICT,
    CONSTRAINT chk_investment_paper_account_state CHECK (
        initial_equity > 0
        AND wallet_balance >= 0
        AND ledger_sequence >= 0
        AND margin_asset = 'USDT'
        AND margin_mode = 'ISOLATED'
        AND position_mode = 'ONE_WAY'
        AND status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')
    )
);
CREATE INDEX idx_investment_paper_account_workspace_status
    ON investment_paper_account (workspace_id, status, created_at DESC);

CREATE TABLE investment_risk_profile (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    max_leverage NUMERIC(24, 12) NOT NULL,
    max_order_notional NUMERIC(38, 18) NOT NULL,
    max_position_notional NUMERIC(38, 18) NOT NULL,
    max_gross_exposure_notional NUMERIC(38, 18) NOT NULL,
    max_open_positions BIGINT NOT NULL,
    max_daily_loss_amount NUMERIC(38, 18) NOT NULL,
    max_drawdown_ratio NUMERIC(24, 12) NOT NULL,
    max_orders_per_hour BIGINT NOT NULL,
    cooldown_seconds BIGINT NOT NULL,
    max_market_data_age_seconds BIGINT NOT NULL,
    max_slippage_bps NUMERIC(24, 12) NOT NULL,
    settings_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_investment_risk_profile_account UNIQUE (account_id),
    CONSTRAINT fk_investment_risk_profile_account FOREIGN KEY (account_id)
        REFERENCES investment_paper_account (id) ON DELETE RESTRICT,
    CONSTRAINT chk_investment_risk_profile_limits CHECK (
        max_leverage > 0
        AND max_order_notional > 0
        AND max_position_notional > 0
        AND max_gross_exposure_notional > 0
        AND max_position_notional <= max_gross_exposure_notional
        AND max_open_positions > 0
        AND max_daily_loss_amount > 0
        AND max_drawdown_ratio > 0
        AND max_drawdown_ratio <= 1
        AND max_orders_per_hour > 0
        AND cooldown_seconds >= 0
        AND max_market_data_age_seconds > 0
        AND max_slippage_bps >= 0
        AND max_slippage_bps <= 10000
    )
);

CREATE TABLE investment_trade_intent (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_reference_id VARCHAR(128),
    idempotency_key VARCHAR(128) NOT NULL,
    position_action VARCHAR(32) NOT NULL,
    side VARCHAR(16) NOT NULL,
    order_type VARCHAR(16) NOT NULL,
    quantity NUMERIC(38, 18) NOT NULL,
    requested_notional NUMERIC(38, 18) NOT NULL,
    leverage NUMERIC(24, 12) NOT NULL,
    limit_price NUMERIC(38, 18),
    reduce_only BOOLEAN NOT NULL DEFAULT FALSE,
    reason VARCHAR(2000),
    data_as_of TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL,
    risk_checked_at TIMESTAMP WITH TIME ZONE,
    accepted_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_investment_trade_intent_idempotency UNIQUE (idempotency_key),
    CONSTRAINT uk_investment_trade_intent_scope
        UNIQUE (id, workspace_id, account_id, instrument_id),
    CONSTRAINT fk_investment_trade_intent_account_workspace
        FOREIGN KEY (account_id, workspace_id)
        REFERENCES investment_paper_account (id, workspace_id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_trade_intent_instrument FOREIGN KEY (instrument_id)
        REFERENCES investment_instrument (id) ON DELETE RESTRICT,
    CONSTRAINT chk_investment_trade_intent_values CHECK (
        source_type IN ('USER', 'STRATEGY', 'AGENT', 'SYSTEM')
        AND position_action IN ('OPEN', 'INCREASE', 'REDUCE', 'CLOSE')
        AND side IN ('BUY', 'SELL')
        AND order_type IN ('MARKET', 'LIMIT')
        AND quantity > 0
        AND requested_notional > 0
        AND leverage > 0
        AND ((order_type = 'MARKET' AND limit_price IS NULL)
            OR (order_type = 'LIMIT' AND limit_price > 0))
        AND status IN ('RECEIVED', 'RISK_REJECTED', 'ACCEPTED', 'EXPIRED', 'FAILED')
        AND (expires_at IS NULL OR expires_at > created_at)
    )
);

CREATE INDEX idx_investment_trade_intent_account_created
    ON investment_trade_intent (account_id, created_at DESC);

CREATE INDEX idx_investment_trade_intent_source_reference
    ON investment_trade_intent (source_type, source_reference_id, created_at DESC);

CREATE TABLE investment_risk_check (
    id BIGSERIAL PRIMARY KEY,
    intent_id BIGINT NOT NULL,
    rule_code VARCHAR(128) NOT NULL,
    passed BOOLEAN NOT NULL,
    observed_value NUMERIC(38, 18),
    limit_value NUMERIC(38, 18),
    message VARCHAR(2000),
    details_json JSONB NOT NULL DEFAULT '{}',
    checked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_investment_risk_check_rule UNIQUE (intent_id, rule_code),
    CONSTRAINT fk_investment_risk_check_intent FOREIGN KEY (intent_id)
        REFERENCES investment_trade_intent (id) ON DELETE RESTRICT
);

CREATE INDEX idx_investment_risk_check_intent_checked
    ON investment_risk_check (intent_id, checked_at);

CREATE TABLE investment_paper_order (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    intent_id BIGINT NOT NULL,
    client_order_id VARCHAR(128) NOT NULL,
    instrument_id BIGINT NOT NULL,
    origin VARCHAR(32) NOT NULL,
    position_action VARCHAR(32) NOT NULL,
    side VARCHAR(16) NOT NULL,
    order_type VARCHAR(16) NOT NULL,
    time_in_force VARCHAR(16) NOT NULL,
    quantity NUMERIC(38, 18) NOT NULL,
    remaining_quantity NUMERIC(38, 18) NOT NULL,
    leverage NUMERIC(24, 12) NOT NULL,
    limit_price NUMERIC(38, 18),
    reduce_only BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    matched_at TIMESTAMP WITH TIME ZONE,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    rejection_code VARCHAR(64),
    rejection_message VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_investment_paper_order_client UNIQUE (client_order_id),
    CONSTRAINT uk_investment_paper_order_intent UNIQUE (intent_id),
    CONSTRAINT uk_investment_paper_order_instrument_id UNIQUE (id, instrument_id),
    CONSTRAINT fk_investment_paper_order_account_workspace
        FOREIGN KEY (account_id, workspace_id)
        REFERENCES investment_paper_account (id, workspace_id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_paper_order_intent_scope
        FOREIGN KEY (intent_id, workspace_id, account_id, instrument_id)
        REFERENCES investment_trade_intent (id, workspace_id, account_id, instrument_id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_investment_paper_order_values CHECK (
        origin IN ('USER', 'STRATEGY', 'AGENT', 'LIQUIDATION')
        AND position_action IN ('OPEN', 'INCREASE', 'REDUCE', 'CLOSE')
        AND side IN ('BUY', 'SELL')
        AND order_type IN ('MARKET', 'LIMIT')
        AND time_in_force IN ('GTC', 'IOC', 'FOK')
        AND quantity > 0
        AND remaining_quantity >= 0
        AND remaining_quantity <= quantity
        AND leverage > 0
        AND ((order_type = 'MARKET' AND limit_price IS NULL)
            OR (order_type = 'LIMIT' AND limit_price > 0))
        AND status IN ('PENDING_MATCH', 'FILLED', 'CANCELLED', 'EXPIRED', 'REJECTED', 'FAILED')
        AND (matched_at IS NULL OR matched_at >= submitted_at)
        AND (cancelled_at IS NULL OR cancelled_at >= submitted_at)
    )
);

CREATE INDEX idx_investment_paper_order_account_status_submitted
    ON investment_paper_order (account_id, status, submitted_at);

CREATE INDEX idx_investment_paper_order_instrument_status_submitted
    ON investment_paper_order (instrument_id, status, submitted_at);

CREATE TABLE investment_paper_fill (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    fill_no BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,
    position_action VARCHAR(32) NOT NULL,
    side VARCHAR(16) NOT NULL,
    fill_price NUMERIC(38, 18) NOT NULL,
    quantity NUMERIC(38, 18) NOT NULL,
    notional NUMERIC(38, 18) NOT NULL,
    fee_rate NUMERIC(24, 12) NOT NULL,
    fee_amount NUMERIC(38, 18) NOT NULL,
    fee_asset VARCHAR(32) NOT NULL,
    liquidity VARCHAR(16) NOT NULL,
    filled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    market_bar_open_time TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_investment_paper_fill_number UNIQUE (order_id, fill_no),
    CONSTRAINT fk_investment_paper_fill_order_instrument
        FOREIGN KEY (order_id, instrument_id)
        REFERENCES investment_paper_order (id, instrument_id) ON DELETE RESTRICT,
    CONSTRAINT chk_investment_paper_fill_values CHECK (
        fill_no > 0
        AND position_action IN ('OPEN', 'INCREASE', 'REDUCE', 'CLOSE')
        AND side IN ('BUY', 'SELL')
        AND fill_price > 0
        AND quantity > 0
        AND notional > 0
        AND fee_rate >= 0
        AND fee_amount >= 0
        AND fee_asset = 'USDT'
        AND liquidity IN ('MAKER', 'TAKER')
    )
);

CREATE INDEX idx_investment_paper_fill_order_filled
    ON investment_paper_fill (order_id, filled_at, fill_no);

CREATE TABLE investment_margin_ledger (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    sequence_no BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    asset VARCHAR(32) NOT NULL,
    amount NUMERIC(38, 18) NOT NULL,
    balance_after NUMERIC(38, 18) NOT NULL,
    instrument_id BIGINT,
    reference_type VARCHAR(64) NOT NULL,
    reference_id VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    details_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_investment_margin_ledger_sequence UNIQUE (account_id, sequence_no),
    CONSTRAINT uk_investment_margin_ledger_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_investment_margin_ledger_account FOREIGN KEY (account_id)
        REFERENCES investment_paper_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_margin_ledger_instrument FOREIGN KEY (instrument_id)
        REFERENCES investment_instrument (id) ON DELETE RESTRICT,
    CONSTRAINT chk_investment_margin_ledger_values CHECK (
        sequence_no > 0
        AND event_type IN ('DEPOSIT', 'MARGIN_RESERVE', 'MARGIN_RELEASE', 'FEE',
            'FUNDING', 'REALIZED_PNL', 'LIQUIDATION')
        AND asset = 'USDT'
        AND balance_after >= 0
        AND CHAR_LENGTH(reference_type) > 0
        AND CHAR_LENGTH(reference_id) > 0
    )
);

CREATE INDEX idx_investment_margin_ledger_account_occurred
    ON investment_margin_ledger (account_id, occurred_at DESC);

CREATE INDEX idx_investment_margin_ledger_reference
    ON investment_margin_ledger (reference_type, reference_id);

CREATE TABLE investment_position (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,
    position_side VARCHAR(16) NOT NULL,
    quantity NUMERIC(38, 18) NOT NULL,
    entry_price NUMERIC(38, 18) NOT NULL,
    leverage NUMERIC(24, 12) NOT NULL,
    isolated_margin NUMERIC(38, 18) NOT NULL,
    maintenance_margin_rate NUMERIC(24, 12) NOT NULL,
    maintenance_margin NUMERIC(38, 18) NOT NULL,
    liquidation_price NUMERIC(38, 18) NOT NULL,
    realized_pnl NUMERIC(38, 18) NOT NULL DEFAULT 0,
    funding_pnl NUMERIC(38, 18) NOT NULL DEFAULT 0,
    last_fill_at TIMESTAMP WITH TIME ZONE,
    last_margin_check_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_investment_position_instrument UNIQUE (account_id, instrument_id),
    CONSTRAINT fk_investment_position_account FOREIGN KEY (account_id)
        REFERENCES investment_paper_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_position_instrument FOREIGN KEY (instrument_id)
        REFERENCES investment_instrument (id) ON DELETE RESTRICT,
    CONSTRAINT chk_investment_position_values CHECK (
        position_side IN ('LONG', 'SHORT')
        AND quantity > 0
        AND entry_price > 0
        AND leverage > 0
        AND isolated_margin >= 0
        AND maintenance_margin_rate >= 0
        AND maintenance_margin_rate < 1
        AND maintenance_margin >= 0
        AND liquidation_price > 0
    )
);

CREATE INDEX idx_investment_position_account_side
    ON investment_position (account_id, position_side, instrument_id);

CREATE TABLE investment_account_snapshot (
    account_id BIGINT NOT NULL,
    snapshot_time TIMESTAMP WITH TIME ZONE NOT NULL,
    wallet_balance NUMERIC(38, 18) NOT NULL,
    used_margin NUMERIC(38, 18) NOT NULL,
    maintenance_margin NUMERIC(38, 18) NOT NULL,
    unrealized_pnl NUMERIC(38, 18) NOT NULL,
    equity NUMERIC(38, 18) NOT NULL,
    available_balance NUMERIC(38, 18) NOT NULL,
    gross_exposure NUMERIC(38, 18) NOT NULL,
    total_return NUMERIC(24, 12) NOT NULL,
    drawdown NUMERIC(24, 12) NOT NULL,
    position_count BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_investment_account_snapshot PRIMARY KEY (account_id, snapshot_time),
    CONSTRAINT fk_investment_account_snapshot_account FOREIGN KEY (account_id)
        REFERENCES investment_paper_account (id) ON DELETE RESTRICT,
    CONSTRAINT chk_investment_account_snapshot_values CHECK (
        used_margin >= 0
        AND maintenance_margin >= 0
        AND gross_exposure >= 0
        AND drawdown >= 0
        AND drawdown <= 1
        AND position_count >= 0
    )
);
