-- V40: create Investment market data, research, and durable job foundation.

CREATE TABLE investment_venue (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    status VARCHAR(32) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_investment_venue_code UNIQUE (code),
    CONSTRAINT chk_investment_venue_state CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE investment_data_source (
    id BIGSERIAL PRIMARY KEY,
    venue_id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL,
    provider_type VARCHAR(64) NOT NULL,
    api_family VARCHAR(64) NOT NULL,
    product_type VARCHAR(32) NOT NULL,
    capabilities_json JSONB NOT NULL DEFAULT '[]',
    rate_limit_per_second NUMERIC(24, 12) NOT NULL,
    status VARCHAR(32) NOT NULL,
    settings_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_investment_data_source_code UNIQUE (code),
    CONSTRAINT fk_investment_data_source_venue FOREIGN KEY (venue_id)
        REFERENCES investment_venue (id) ON DELETE RESTRICT,
    CONSTRAINT chk_investment_data_source_limit CHECK (rate_limit_per_second > 0),
    CONSTRAINT chk_investment_data_source_state CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_investment_data_source_venue_status
    ON investment_data_source (venue_id, status);

CREATE TABLE investment_instrument (
    id BIGSERIAL PRIMARY KEY,
    venue_id BIGINT NOT NULL,
    market_type VARCHAR(32) NOT NULL,
    product_type VARCHAR(32) NOT NULL,
    contract_type VARCHAR(32) NOT NULL,
    symbol VARCHAR(128) NOT NULL,
    base_asset VARCHAR(32) NOT NULL,
    quote_asset VARCHAR(32) NOT NULL,
    settlement_asset VARCHAR(32) NOT NULL,
    margin_asset VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    launch_time TIMESTAMP WITH TIME ZONE,
    delivery_start_time TIMESTAMP WITH TIME ZONE,
    delivery_time TIMESTAMP WITH TIME ZONE,
    off_time TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_investment_instrument_business UNIQUE (venue_id, product_type, symbol),
    CONSTRAINT fk_investment_instrument_venue FOREIGN KEY (venue_id)
        REFERENCES investment_venue (id) ON DELETE RESTRICT,
    CONSTRAINT chk_investment_instrument_contract CHECK (
        market_type = 'FUTURES'
        AND product_type = 'USDT_FUTURES'
        AND contract_type = 'PERPETUAL'
    ),
    CONSTRAINT chk_investment_instrument_state CHECK (status IN ('ACTIVE', 'SUSPENDED', 'OFFLINE'))
);

CREATE INDEX idx_investment_instrument_product_status_quote
    ON investment_instrument (product_type, status, quote_asset);

CREATE TABLE investment_instrument_source (
    id BIGSERIAL PRIMARY KEY,
    instrument_id BIGINT NOT NULL,
    source_id BIGINT NOT NULL,
    external_symbol VARCHAR(128) NOT NULL,
    external_product_type VARCHAR(64) NOT NULL,
    source_status VARCHAR(32) NOT NULL,
    raw_metadata_json JSONB NOT NULL DEFAULT '{}',
    last_synced_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_investment_instrument_source_external
        UNIQUE (source_id, external_product_type, external_symbol),
    CONSTRAINT uk_investment_instrument_source_mapping UNIQUE (instrument_id, source_id),
    CONSTRAINT fk_investment_instrument_source_instrument FOREIGN KEY (instrument_id)
        REFERENCES investment_instrument (id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_instrument_source_source FOREIGN KEY (source_id)
        REFERENCES investment_data_source (id) ON DELETE RESTRICT,
    CONSTRAINT chk_investment_instrument_source_state CHECK (source_status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE investment_contract_spec (
    instrument_id BIGINT PRIMARY KEY,
    source_id BIGINT NOT NULL,
    price_precision INTEGER NOT NULL,
    quantity_precision INTEGER NOT NULL,
    price_end_step NUMERIC(38, 18) NOT NULL,
    quantity_step NUMERIC(38, 18) NOT NULL,
    contract_multiplier NUMERIC(38, 18) NOT NULL,
    min_trade_quantity NUMERIC(38, 18) NOT NULL,
    min_trade_notional NUMERIC(38, 18) NOT NULL,
    max_market_order_quantity NUMERIC(38, 18) NOT NULL,
    max_limit_order_quantity NUMERIC(38, 18) NOT NULL,
    maker_fee_rate NUMERIC(24, 12) NOT NULL,
    taker_fee_rate NUMERIC(24, 12) NOT NULL,
    min_leverage NUMERIC(24, 12) NOT NULL,
    max_leverage NUMERIC(24, 12) NOT NULL,
    funding_interval_hours INTEGER NOT NULL,
    buy_limit_price_ratio NUMERIC(24, 12) NOT NULL,
    sell_limit_price_ratio NUMERIC(24, 12) NOT NULL,
    source_updated_at TIMESTAMP WITH TIME ZONE,
    ingested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revision BIGINT NOT NULL DEFAULT 1,
    raw_metadata_json JSONB NOT NULL DEFAULT '{}',
    CONSTRAINT fk_investment_contract_spec_instrument FOREIGN KEY (instrument_id)
        REFERENCES investment_instrument (id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_contract_spec_source FOREIGN KEY (source_id)
        REFERENCES investment_data_source (id) ON DELETE RESTRICT,
    CONSTRAINT chk_investment_contract_spec_positive CHECK (
        price_precision >= 0
        AND quantity_precision >= 0
        AND price_end_step > 0
        AND quantity_step > 0
        AND contract_multiplier > 0
        AND min_trade_quantity > 0
        AND min_trade_notional > 0
        AND max_market_order_quantity > 0
        AND max_limit_order_quantity > 0
        AND maker_fee_rate >= 0
        AND taker_fee_rate >= 0
        AND min_leverage > 0
        AND max_leverage >= min_leverage
        AND funding_interval_hours > 0
        AND buy_limit_price_ratio > 0
        AND sell_limit_price_ratio > 0
        AND revision > 0
    )
);

CREATE TABLE investment_position_tier (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    tier_level INTEGER NOT NULL,
    start_notional NUMERIC(38, 18) NOT NULL,
    end_notional NUMERIC(38, 18) NOT NULL,
    max_leverage NUMERIC(24, 12) NOT NULL,
    maintenance_margin_rate NUMERIC(24, 12) NOT NULL,
    source_hash VARCHAR(128) NOT NULL,
    ingested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_investment_position_tier_snapshot
        UNIQUE (source_id, instrument_id, observed_at, tier_level),
    CONSTRAINT fk_investment_position_tier_source FOREIGN KEY (source_id)
        REFERENCES investment_data_source (id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_position_tier_instrument FOREIGN KEY (instrument_id)
        REFERENCES investment_instrument (id) ON DELETE RESTRICT,
    CONSTRAINT chk_investment_position_tier_values CHECK (
        tier_level > 0
        AND start_notional >= 0
        AND end_notional > start_notional
        AND max_leverage > 0
        AND maintenance_margin_rate >= 0
        AND maintenance_margin_rate < 1
        AND last_seen_at >= observed_at
    )
);

CREATE INDEX idx_investment_position_tier_instrument_observed
    ON investment_position_tier (instrument_id, observed_at DESC, tier_level);

CREATE TABLE investment_market_bar_daily (
    source_id BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,
    price_type VARCHAR(32) NOT NULL,
    bar_date DATE NOT NULL,
    open_price NUMERIC(38, 18) NOT NULL,
    high_price NUMERIC(38, 18) NOT NULL,
    low_price NUMERIC(38, 18) NOT NULL,
    close_price NUMERIC(38, 18) NOT NULL,
    base_volume NUMERIC(38, 18) NOT NULL,
    quote_volume NUMERIC(38, 18) NOT NULL,
    is_closed BOOLEAN NOT NULL,
    source_updated_at TIMESTAMP WITH TIME ZONE,
    ingested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revision BIGINT NOT NULL DEFAULT 1,
    revision_slot BIGINT NOT NULL DEFAULT 0,
    valid_from TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_to TIMESTAMP WITH TIME ZONE,
    checksum VARCHAR(128) NOT NULL,
    CONSTRAINT pk_investment_market_bar_daily
        PRIMARY KEY (source_id, instrument_id, price_type, bar_date, revision),
    CONSTRAINT uk_investment_market_bar_daily_slot
        UNIQUE (source_id, instrument_id, price_type, bar_date, revision_slot),
    CONSTRAINT fk_investment_market_bar_daily_source FOREIGN KEY (source_id)
        REFERENCES investment_data_source (id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_market_bar_daily_instrument FOREIGN KEY (instrument_id)
        REFERENCES investment_instrument (id) ON DELETE RESTRICT,
    CONSTRAINT chk_investment_market_bar_daily_price_type
        CHECK (price_type IN ('MARKET', 'MARK', 'INDEX')),
    CONSTRAINT chk_investment_market_bar_daily_ohlc CHECK (
        low_price <= open_price
        AND low_price <= close_price
        AND open_price <= high_price
        AND close_price <= high_price
        AND low_price <= high_price
    ),
    CONSTRAINT chk_investment_market_bar_daily_positive CHECK (
        open_price > 0
        AND high_price > 0
        AND low_price > 0
        AND close_price > 0
        AND base_volume >= 0
        AND quote_volume >= 0
    ),
    CONSTRAINT chk_investment_market_bar_daily_revision CHECK (revision > 0 AND revision_slot >= 0),
    CONSTRAINT chk_investment_market_bar_daily_revision_state CHECK (
        (revision_slot = 0 AND valid_to IS NULL)
        OR (revision_slot = revision AND valid_to IS NOT NULL AND valid_to > valid_from)
    )
);

CREATE INDEX idx_investment_market_bar_daily_lookup
    ON investment_market_bar_daily (instrument_id, price_type, revision_slot, bar_date DESC);

CREATE TABLE investment_market_bar_intraday (
    source_id BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,
    price_type VARCHAR(32) NOT NULL,
    interval_code VARCHAR(32) NOT NULL,
    open_time TIMESTAMP WITH TIME ZONE NOT NULL,
    close_time TIMESTAMP WITH TIME ZONE NOT NULL,
    open_price NUMERIC(38, 18) NOT NULL,
    high_price NUMERIC(38, 18) NOT NULL,
    low_price NUMERIC(38, 18) NOT NULL,
    close_price NUMERIC(38, 18) NOT NULL,
    base_volume NUMERIC(38, 18) NOT NULL,
    quote_volume NUMERIC(38, 18) NOT NULL,
    is_closed BOOLEAN NOT NULL,
    source_updated_at TIMESTAMP WITH TIME ZONE,
    ingested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revision BIGINT NOT NULL DEFAULT 1,
    revision_slot BIGINT NOT NULL DEFAULT 0,
    valid_from TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_to TIMESTAMP WITH TIME ZONE,
    checksum VARCHAR(128) NOT NULL,
    CONSTRAINT pk_investment_market_bar_intraday
        PRIMARY KEY (source_id, instrument_id, price_type, interval_code, open_time, revision),
    CONSTRAINT uk_investment_market_bar_intraday_slot
        UNIQUE (source_id, instrument_id, price_type, interval_code, open_time, revision_slot),
    CONSTRAINT fk_investment_market_bar_intraday_source FOREIGN KEY (source_id)
        REFERENCES investment_data_source (id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_market_bar_intraday_instrument FOREIGN KEY (instrument_id)
        REFERENCES investment_instrument (id) ON DELETE RESTRICT,
    CONSTRAINT chk_investment_market_bar_intraday_price_type
        CHECK (price_type IN ('MARKET', 'MARK', 'INDEX')),
    CONSTRAINT chk_investment_market_bar_intraday_interval
        CHECK (interval_code IN ('M1', 'M5', 'M15', 'M30', 'H1', 'H4', 'D1')),
    CONSTRAINT chk_investment_market_bar_intraday_time CHECK (close_time > open_time),
    CONSTRAINT chk_investment_market_bar_intraday_ohlc CHECK (
        low_price <= open_price
        AND low_price <= close_price
        AND open_price <= high_price
        AND close_price <= high_price
        AND low_price <= high_price
    ),
    CONSTRAINT chk_investment_market_bar_intraday_positive CHECK (
        open_price > 0
        AND high_price > 0
        AND low_price > 0
        AND close_price > 0
        AND base_volume >= 0
        AND quote_volume >= 0
    ),
    CONSTRAINT chk_investment_market_bar_intraday_revision CHECK (revision > 0 AND revision_slot >= 0),
    CONSTRAINT chk_investment_market_bar_intraday_revision_state CHECK (
        (revision_slot = 0 AND valid_to IS NULL)
        OR (revision_slot = revision AND valid_to IS NOT NULL AND valid_to > valid_from)
    )
);

CREATE INDEX idx_investment_market_bar_intraday_lookup
    ON investment_market_bar_intraday
        (instrument_id, price_type, interval_code, revision_slot, open_time DESC);

CREATE TABLE investment_contract_quote_latest (
    source_id BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,
    last_price NUMERIC(38, 18) NOT NULL,
    mark_price NUMERIC(38, 18),
    index_price NUMERIC(38, 18),
    bid_price NUMERIC(38, 18),
    ask_price NUMERIC(38, 18),
    bid_quantity NUMERIC(38, 18),
    ask_quantity NUMERIC(38, 18),
    open_24h NUMERIC(38, 18),
    high_24h NUMERIC(38, 18),
    low_24h NUMERIC(38, 18),
    base_volume_24h NUMERIC(38, 18),
    quote_volume_24h NUMERIC(38, 18),
    change_24h NUMERIC(24, 12),
    funding_rate NUMERIC(24, 12),
    next_funding_time TIMESTAMP WITH TIME ZONE,
    open_interest NUMERIC(38, 18),
    source_time TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_investment_contract_quote_latest PRIMARY KEY (source_id, instrument_id),
    CONSTRAINT fk_investment_contract_quote_latest_source FOREIGN KEY (source_id)
        REFERENCES investment_data_source (id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_contract_quote_latest_instrument FOREIGN KEY (instrument_id)
        REFERENCES investment_instrument (id) ON DELETE RESTRICT,
    CONSTRAINT chk_investment_contract_quote_latest_prices CHECK (
        last_price > 0
        AND (mark_price IS NULL OR mark_price > 0)
        AND (index_price IS NULL OR index_price > 0)
        AND (bid_price IS NULL OR bid_price > 0)
        AND (ask_price IS NULL OR ask_price > 0)
        AND (open_24h IS NULL OR open_24h > 0)
        AND (high_24h IS NULL OR high_24h > 0)
        AND (low_24h IS NULL OR low_24h > 0)
    ),
    CONSTRAINT chk_investment_contract_quote_latest_quantities CHECK (
        (bid_quantity IS NULL OR bid_quantity >= 0)
        AND (ask_quantity IS NULL OR ask_quantity >= 0)
        AND (base_volume_24h IS NULL OR base_volume_24h >= 0)
        AND (quote_volume_24h IS NULL OR quote_volume_24h >= 0)
        AND (open_interest IS NULL OR open_interest >= 0)
    )
);

CREATE TABLE investment_funding_rate (
    source_id BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,
    funding_time TIMESTAMP WITH TIME ZONE NOT NULL,
    funding_rate NUMERIC(24, 12) NOT NULL,
    ingested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revision BIGINT NOT NULL DEFAULT 1,
    revision_slot BIGINT NOT NULL DEFAULT 0,
    valid_from TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_to TIMESTAMP WITH TIME ZONE,
    checksum VARCHAR(128) NOT NULL,
    CONSTRAINT pk_investment_funding_rate
        PRIMARY KEY (source_id, instrument_id, funding_time, revision),
    CONSTRAINT uk_investment_funding_rate_slot
        UNIQUE (source_id, instrument_id, funding_time, revision_slot),
    CONSTRAINT fk_investment_funding_rate_source FOREIGN KEY (source_id)
        REFERENCES investment_data_source (id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_funding_rate_instrument FOREIGN KEY (instrument_id)
        REFERENCES investment_instrument (id) ON DELETE RESTRICT,
    CONSTRAINT chk_investment_funding_rate_revision CHECK (revision > 0 AND revision_slot >= 0),
    CONSTRAINT chk_investment_funding_rate_revision_state CHECK (
        (revision_slot = 0 AND valid_to IS NULL)
        OR (revision_slot = revision AND valid_to IS NOT NULL AND valid_to > valid_from)
    )
);

CREATE INDEX idx_investment_funding_rate_lookup
    ON investment_funding_rate (instrument_id, revision_slot, funding_time DESC);

CREATE TABLE investment_workspace (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    base_currency VARCHAR(32) NOT NULL DEFAULT 'USDT',
    timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    settings_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_investment_workspace_owner_name UNIQUE (owner_user_id, name),
    CONSTRAINT chk_investment_workspace_currency CHECK (base_currency = 'USDT'),
    CONSTRAINT chk_investment_workspace_state CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_investment_workspace_owner_status
    ON investment_workspace (owner_user_id, status);

CREATE TABLE investment_ingest_cursor (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,
    data_type VARCHAR(64) NOT NULL,
    price_type VARCHAR(32) NOT NULL DEFAULT 'NONE',
    interval_code VARCHAR(32) NOT NULL DEFAULT 'NONE',
    next_start_time TIMESTAMP WITH TIME ZONE,
    last_success_time TIMESTAMP WITH TIME ZONE,
    last_checksum VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'IDLE',
    last_error TEXT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_investment_ingest_cursor_dimension
        UNIQUE (source_id, instrument_id, data_type, price_type, interval_code),
    CONSTRAINT fk_investment_ingest_cursor_source FOREIGN KEY (source_id)
        REFERENCES investment_data_source (id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_ingest_cursor_instrument FOREIGN KEY (instrument_id)
        REFERENCES investment_instrument (id) ON DELETE RESTRICT,
    CONSTRAINT chk_investment_ingest_cursor_price_type
        CHECK (price_type IN ('NONE', 'MARKET', 'MARK', 'INDEX')),
    CONSTRAINT chk_investment_ingest_cursor_interval
        CHECK (interval_code IN ('NONE', 'M1', 'M5', 'M15', 'M30', 'H1', 'H4', 'D1')),
    CONSTRAINT chk_investment_ingest_cursor_state
        CHECK (status IN ('IDLE', 'RUNNING', 'SUCCEEDED', 'FAILED', 'DISABLED'))
);

CREATE TABLE investment_job (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT,
    job_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    priority INTEGER NOT NULL DEFAULT 100,
    available_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_at TIMESTAMP WITH TIME ZONE,
    locked_by VARCHAR(128),
    claim_token VARCHAR(64),
    lease_expires_at TIMESTAMP WITH TIME ZONE,
    heartbeat_at TIMESTAMP WITH TIME ZONE,
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    idempotency_key VARCHAR(256) NOT NULL,
    input_json JSONB NOT NULL DEFAULT '{}',
    result_json JSONB NOT NULL DEFAULT '{}',
    last_error_code VARCHAR(64),
    last_error_message TEXT,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_investment_job_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_investment_job_workspace FOREIGN KEY (workspace_id)
        REFERENCES investment_workspace (id) ON DELETE RESTRICT,
    CONSTRAINT chk_investment_job_state CHECK (
        status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')
        AND attempts >= 0
        AND max_attempts > 0
        AND attempts <= max_attempts
    ),
    CONSTRAINT chk_investment_job_lease_state CHECK (
        status <> 'RUNNING'
        OR (
            locked_at IS NOT NULL
            AND locked_by IS NOT NULL
            AND claim_token IS NOT NULL
            AND lease_expires_at IS NOT NULL
            AND heartbeat_at IS NOT NULL
        )
    )
);

CREATE INDEX idx_investment_job_dispatch
    ON investment_job (status, available_at, priority, id);
CREATE INDEX idx_investment_job_workspace_created
    ON investment_job (workspace_id, created_at DESC);

CREATE TABLE investment_data_quality_issue (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL,
    source_id BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,
    data_type VARCHAR(64) NOT NULL,
    price_type VARCHAR(32) NOT NULL DEFAULT 'NONE',
    interval_code VARCHAR(32) NOT NULL DEFAULT 'NONE',
    point_time TIMESTAMP WITH TIME ZONE NOT NULL,
    issue_code VARCHAR(64) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    details_json JSONB NOT NULL DEFAULT '{}',
    resolution_status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    resolved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_investment_data_quality_job FOREIGN KEY (job_id)
        REFERENCES investment_job (id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_data_quality_source FOREIGN KEY (source_id)
        REFERENCES investment_data_source (id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_data_quality_instrument FOREIGN KEY (instrument_id)
        REFERENCES investment_instrument (id) ON DELETE RESTRICT,
    CONSTRAINT chk_investment_data_quality_price_type
        CHECK (price_type IN ('NONE', 'MARKET', 'MARK', 'INDEX')),
    CONSTRAINT chk_investment_data_quality_interval
        CHECK (interval_code IN ('NONE', 'M1', 'M5', 'M15', 'M30', 'H1', 'H4', 'D1')),
    CONSTRAINT chk_investment_data_quality_severity CHECK (severity IN ('INFO', 'WARNING', 'ERROR')),
    CONSTRAINT chk_investment_data_quality_state CHECK (
        (resolution_status = 'OPEN' AND resolved_at IS NULL)
        OR (resolution_status IN ('RESOLVED', 'IGNORED') AND resolved_at IS NOT NULL)
    )
);

CREATE INDEX idx_investment_data_quality_resolution
    ON investment_data_quality_issue (resolution_status, severity, created_at);
CREATE INDEX idx_investment_data_quality_instrument_time
    ON investment_data_quality_issue (instrument_id, point_time DESC);

CREATE TABLE investment_watchlist (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512) NOT NULL DEFAULT '',
    sort_no INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_investment_watchlist_workspace_name UNIQUE (workspace_id, name),
    CONSTRAINT fk_investment_watchlist_workspace FOREIGN KEY (workspace_id)
        REFERENCES investment_workspace (id) ON DELETE RESTRICT,
    CONSTRAINT chk_investment_watchlist_sort CHECK (sort_no >= 0)
);

CREATE TABLE investment_watchlist_item (
    id BIGSERIAL PRIMARY KEY,
    watchlist_id BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,
    sort_no INTEGER NOT NULL DEFAULT 0,
    note VARCHAR(512) NOT NULL DEFAULT '',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_investment_watchlist_item_instrument UNIQUE (watchlist_id, instrument_id),
    CONSTRAINT fk_investment_watchlist_item_watchlist FOREIGN KEY (watchlist_id)
        REFERENCES investment_watchlist (id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_watchlist_item_instrument FOREIGN KEY (instrument_id)
        REFERENCES investment_instrument (id) ON DELETE RESTRICT,
    CONSTRAINT chk_investment_watchlist_item_sort CHECK (sort_no >= 0)
);

CREATE TABLE investment_research_report (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    instrument_id BIGINT,
    report_type VARCHAR(64) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_reference_id BIGINT,
    title VARCHAR(256) NOT NULL,
    summary TEXT NOT NULL DEFAULT '',
    content_markdown TEXT NOT NULL DEFAULT '',
    metrics_json JSONB NOT NULL DEFAULT '{}',
    data_as_of TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    report_version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_investment_research_report_workspace FOREIGN KEY (workspace_id)
        REFERENCES investment_workspace (id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_research_report_instrument FOREIGN KEY (instrument_id)
        REFERENCES investment_instrument (id) ON DELETE RESTRICT,
    CONSTRAINT chk_investment_research_report_source
        CHECK (source_type IN ('USER', 'SYSTEM', 'STRATEGY', 'AGENT')),
    CONSTRAINT chk_investment_research_report_state CHECK (
        status IN ('PENDING', 'READY', 'FAILED')
        AND report_version > 0
    )
);

CREATE INDEX idx_investment_research_report_workspace_created
    ON investment_research_report (workspace_id, created_at DESC);
CREATE INDEX idx_investment_research_report_instrument_as_of
    ON investment_research_report (instrument_id, data_as_of DESC);

CREATE TABLE investment_report_evidence (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL,
    evidence_type VARCHAR(64) NOT NULL,
    source_id BIGINT NOT NULL,
    instrument_id BIGINT,
    data_start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    data_end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    data_as_of TIMESTAMP WITH TIME ZONE NOT NULL,
    source_reference VARCHAR(512) NOT NULL,
    payload_hash VARCHAR(128) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_investment_report_evidence_report FOREIGN KEY (report_id)
        REFERENCES investment_research_report (id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_report_evidence_source FOREIGN KEY (source_id)
        REFERENCES investment_data_source (id) ON DELETE RESTRICT,
    CONSTRAINT fk_investment_report_evidence_instrument FOREIGN KEY (instrument_id)
        REFERENCES investment_instrument (id) ON DELETE RESTRICT,
    CONSTRAINT chk_investment_report_evidence_window CHECK (
        data_end_time >= data_start_time
        AND data_as_of >= data_end_time
    )
);

CREATE INDEX idx_investment_report_evidence_report
    ON investment_report_evidence (report_id, created_at);
