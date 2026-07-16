-- V41: create immutable strategy, dataset, and deterministic futures backtest schema.

CREATE TABLE investment_strategy_release (
    id BIGSERIAL PRIMARY KEY,
    strategy_code VARCHAR(128) NOT NULL,
    strategy_version VARCHAR(64) NOT NULL,
    display_name VARCHAR(256) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    implementation_class VARCHAR(512) NOT NULL,
    engine_type VARCHAR(32) NOT NULL,
    build_revision VARCHAR(128) NOT NULL,
    source_hash VARCHAR(64) NOT NULL,
    required_capabilities_json JSONB NOT NULL DEFAULT '[]',
    descriptor_snapshot_json JSONB NOT NULL DEFAULT '{}',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_investment_strategy_release_version
        UNIQUE (strategy_code, strategy_version),
    CONSTRAINT chk_investment_strategy_release_engine
        CHECK (engine_type = 'JAVA'),
    CONSTRAINT chk_investment_strategy_release_hash
        CHECK (CHAR_LENGTH(source_hash) = 64)
);

CREATE INDEX idx_investment_strategy_release_active
    ON investment_strategy_release (active, strategy_code, strategy_version);

CREATE TABLE investment_dataset_snapshot (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    source_id BIGINT NOT NULL,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    data_as_of TIMESTAMP WITH TIME ZONE NOT NULL,
    intervals_json JSONB NOT NULL DEFAULT '[]',
    price_types_json JSONB NOT NULL DEFAULT '[]',
    contract_spec_hash VARCHAR(64) NOT NULL,
    position_tier_hash VARCHAR(64) NOT NULL,
    funding_data_hash VARCHAR(64) NOT NULL,
    contract_spec_snapshot_json JSONB NOT NULL DEFAULT '{}',
    position_tier_snapshot_json JSONB NOT NULL DEFAULT '[]',
    fee_model_snapshot_json JSONB NOT NULL DEFAULT '{}',
    slippage_model_snapshot_json JSONB NOT NULL DEFAULT '{}',
    dataset_hash VARCHAR(64) NOT NULL,
    quality_status VARCHAR(32) NOT NULL,
    artifact_uri VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL,
    CONSTRAINT uk_investment_dataset_snapshot_hash UNIQUE (workspace_id, dataset_hash),
    CONSTRAINT fk_investment_dataset_snapshot_workspace FOREIGN KEY (workspace_id)
        REFERENCES investment_workspace (id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_dataset_snapshot_source FOREIGN KEY (source_id)
        REFERENCES investment_data_source (id) ON DELETE RESTRICT,
    CONSTRAINT chk_investment_dataset_snapshot_time CHECK (
        end_time > start_time
        AND data_as_of >= end_time
    ),
    CONSTRAINT chk_investment_dataset_snapshot_hashes CHECK (
        CHAR_LENGTH(contract_spec_hash) = 64
        AND CHAR_LENGTH(position_tier_hash) = 64
        AND CHAR_LENGTH(funding_data_hash) = 64
        AND CHAR_LENGTH(dataset_hash) = 64
    ),
    CONSTRAINT chk_investment_dataset_snapshot_quality CHECK (
        quality_status IN ('PENDING', 'VERIFIED', 'INVALID', 'EXPIRED', 'NOT_REPRODUCIBLE')
    )
);

CREATE INDEX idx_investment_dataset_snapshot_workspace_created
    ON investment_dataset_snapshot (workspace_id, created_at DESC);

CREATE INDEX idx_investment_dataset_snapshot_source_time
    ON investment_dataset_snapshot (source_id, start_time, end_time, data_as_of);

CREATE TABLE investment_dataset_snapshot_item (
    id BIGSERIAL PRIMARY KEY,
    snapshot_id BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,
    data_type VARCHAR(32) NOT NULL,
    price_type VARCHAR(32) NOT NULL,
    interval_code VARCHAR(32) NOT NULL,
    first_time TIMESTAMP WITH TIME ZONE NOT NULL,
    last_time TIMESTAMP WITH TIME ZONE NOT NULL,
    row_count BIGINT NOT NULL,
    max_revision BIGINT NOT NULL,
    data_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_investment_dataset_snapshot_item_dimension
        UNIQUE (snapshot_id, instrument_id, data_type, price_type, interval_code),
    CONSTRAINT fk_investment_dataset_snapshot_item_snapshot FOREIGN KEY (snapshot_id)
        REFERENCES investment_dataset_snapshot (id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_dataset_snapshot_item_instrument FOREIGN KEY (instrument_id)
        REFERENCES investment_instrument (id) ON DELETE RESTRICT,
    CONSTRAINT chk_investment_dataset_snapshot_item_dimension CHECK (
        data_type IN ('BAR_DAILY', 'BAR_INTRADAY', 'FUNDING_RATE', 'CONTRACT_SPEC', 'POSITION_TIER')
        AND price_type IN ('NONE', 'MARKET', 'MARK', 'INDEX')
        AND interval_code IN ('NONE', 'M1', 'M5', 'M15', 'M30', 'H1', 'H4', 'D1')
        AND (
            (data_type IN ('BAR_DAILY', 'BAR_INTRADAY')
                AND price_type <> 'NONE' AND interval_code <> 'NONE')
            OR
            (data_type IN ('FUNDING_RATE', 'CONTRACT_SPEC', 'POSITION_TIER')
                AND price_type = 'NONE' AND interval_code = 'NONE')
        )
    ),
    CONSTRAINT chk_investment_dataset_snapshot_item_range CHECK (
        last_time >= first_time AND row_count > 0 AND max_revision >= 0
    ),
    CONSTRAINT chk_investment_dataset_snapshot_item_hash
        CHECK (CHAR_LENGTH(data_hash) = 64)
);

CREATE INDEX idx_investment_dataset_snapshot_item_instrument
    ON investment_dataset_snapshot_item (instrument_id, data_type, first_time, last_time);

CREATE TABLE investment_backtest_run (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    job_id BIGINT NOT NULL,
    strategy_release_id BIGINT NOT NULL,
    dataset_snapshot_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    initial_equity NUMERIC(38, 18) NOT NULL,
    base_currency VARCHAR(32) NOT NULL,
    margin_mode VARCHAR(32) NOT NULL,
    position_mode VARCHAR(32) NOT NULL,
    fee_model_code VARCHAR(128) NOT NULL,
    slippage_model_code VARCHAR(128) NOT NULL,
    matching_model_code VARCHAR(128) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    error_code VARCHAR(64),
    error_message VARCHAR(2000),
    total_return NUMERIC(24, 12),
    annualized_return NUMERIC(24, 12),
    max_drawdown NUMERIC(24, 12),
    sharpe_ratio NUMERIC(24, 12),
    sortino_ratio NUMERIC(24, 12),
    win_rate NUMERIC(24, 12),
    profit_factor NUMERIC(38, 18),
    turnover NUMERIC(38, 18),
    trade_count BIGINT,
    total_fee NUMERIC(38, 18),
    total_funding NUMERIC(38, 18),
    liquidation_count BIGINT,
    extra_metrics_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_investment_backtest_run_job UNIQUE (job_id),
    CONSTRAINT fk_investment_backtest_run_workspace FOREIGN KEY (workspace_id)
        REFERENCES investment_workspace (id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_backtest_run_job FOREIGN KEY (job_id)
        REFERENCES investment_job (id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_backtest_run_strategy FOREIGN KEY (strategy_release_id)
        REFERENCES investment_strategy_release (id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_backtest_run_dataset FOREIGN KEY (dataset_snapshot_id)
        REFERENCES investment_dataset_snapshot (id) ON DELETE RESTRICT,
    CONSTRAINT chk_investment_backtest_run_state CHECK (
        status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')
        AND initial_equity > 0
        AND base_currency = 'USDT'
        AND margin_mode = 'ISOLATED'
        AND position_mode = 'ONE_WAY'
        AND (finished_at IS NULL OR started_at IS NULL OR finished_at >= started_at)
    ),
    CONSTRAINT chk_investment_backtest_run_metrics CHECK (
        (max_drawdown IS NULL OR (max_drawdown >= 0 AND max_drawdown <= 1))
        AND (win_rate IS NULL OR (win_rate >= 0 AND win_rate <= 1))
        AND (profit_factor IS NULL OR profit_factor >= 0)
        AND (turnover IS NULL OR turnover >= 0)
        AND (trade_count IS NULL OR trade_count >= 0)
        AND (total_fee IS NULL OR total_fee >= 0)
        AND (liquidation_count IS NULL OR liquidation_count >= 0)
    )
);

CREATE INDEX idx_investment_backtest_run_workspace_created
    ON investment_backtest_run (workspace_id, created_at DESC);

CREATE INDEX idx_investment_backtest_run_strategy_created
    ON investment_backtest_run (strategy_release_id, created_at DESC);

CREATE TABLE investment_backtest_trade (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,
    position_side VARCHAR(16) NOT NULL,
    entry_time TIMESTAMP WITH TIME ZONE NOT NULL,
    exit_time TIMESTAMP WITH TIME ZONE NOT NULL,
    entry_price NUMERIC(38, 18) NOT NULL,
    exit_price NUMERIC(38, 18) NOT NULL,
    quantity NUMERIC(38, 18) NOT NULL,
    leverage NUMERIC(24, 12) NOT NULL,
    gross_pnl NUMERIC(38, 18) NOT NULL,
    fee_amount NUMERIC(38, 18) NOT NULL,
    funding_amount NUMERIC(38, 18) NOT NULL,
    net_pnl NUMERIC(38, 18) NOT NULL,
    exit_reason VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_investment_backtest_trade_run FOREIGN KEY (run_id)
        REFERENCES investment_backtest_run (id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_backtest_trade_instrument FOREIGN KEY (instrument_id)
        REFERENCES investment_instrument (id) ON DELETE RESTRICT,
    CONSTRAINT chk_investment_backtest_trade_values CHECK (
        position_side IN ('LONG', 'SHORT')
        AND exit_time >= entry_time
        AND entry_price > 0
        AND exit_price > 0
        AND quantity > 0
        AND leverage > 0
        AND fee_amount >= 0
    )
);

CREATE INDEX idx_investment_backtest_trade_run_entry
    ON investment_backtest_trade (run_id, entry_time);

CREATE INDEX idx_investment_backtest_trade_run_instrument_entry
    ON investment_backtest_trade (run_id, instrument_id, entry_time);

CREATE TABLE investment_backtest_event (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL,
    instrument_id BIGINT,
    event_type VARCHAR(32) NOT NULL,
    event_time TIMESTAMP WITH TIME ZONE NOT NULL,
    amount NUMERIC(38, 18),
    balance_after NUMERIC(38, 18),
    details_json JSONB NOT NULL DEFAULT '{}',
    sequence_no BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_investment_backtest_event_sequence UNIQUE (run_id, sequence_no),
    CONSTRAINT fk_investment_backtest_event_run FOREIGN KEY (run_id)
        REFERENCES investment_backtest_run (id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_backtest_event_instrument FOREIGN KEY (instrument_id)
        REFERENCES investment_instrument (id) ON DELETE RESTRICT,
    CONSTRAINT chk_investment_backtest_event_type CHECK (
        event_type IN ('FILL', 'FEE', 'FUNDING', 'RISK_REJECTED', 'MARGIN_CHANGE', 'LIQUIDATION')
        AND sequence_no > 0
    )
);

CREATE INDEX idx_investment_backtest_event_run_time
    ON investment_backtest_event (run_id, event_time, sequence_no);

CREATE TABLE investment_backtest_equity_point (
    run_id BIGINT NOT NULL,
    point_time TIMESTAMP WITH TIME ZONE NOT NULL,
    wallet_balance NUMERIC(38, 18) NOT NULL,
    used_margin NUMERIC(38, 18) NOT NULL,
    unrealized_pnl NUMERIC(38, 18) NOT NULL,
    equity NUMERIC(38, 18) NOT NULL,
    drawdown NUMERIC(24, 12) NOT NULL,
    gross_exposure NUMERIC(38, 18) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_investment_backtest_equity_point PRIMARY KEY (run_id, point_time),
    CONSTRAINT fk_investment_backtest_equity_run FOREIGN KEY (run_id)
        REFERENCES investment_backtest_run (id) ON DELETE RESTRICT,
    CONSTRAINT chk_investment_backtest_equity_values CHECK (
        used_margin >= 0
        AND drawdown >= 0
        AND drawdown <= 1
        AND gross_exposure >= 0
    )
);
