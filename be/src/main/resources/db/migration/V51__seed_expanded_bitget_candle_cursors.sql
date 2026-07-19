-- V51: align Bitget candle cursors with the code-declared intraday subscriptions.

INSERT INTO investment_ingest_cursor (
    source_id, instrument_id, data_type, price_type, interval_code, status
)
SELECT mapping.source_id, mapping.instrument_id, 'BAR_INTRADAY', 'MARKET',
       dimension.interval_code, 'IDLE'
FROM investment_instrument_source mapping
JOIN investment_data_source source ON source.id = mapping.source_id
JOIN investment_instrument instrument ON instrument.id = mapping.instrument_id
CROSS JOIN (
    VALUES ('M5'), ('M15'), ('M30'), ('H1'), ('H4')
) AS dimension(interval_code)
WHERE source.code = 'BITGET'
  AND source.status = 'ACTIVE'
  AND source.deleted = FALSE
  AND mapping.source_status = 'ACTIVE'
  AND mapping.deleted = FALSE
  AND instrument.product_type = 'USDT_FUTURES'
  AND instrument.symbol IN ('BTCUSDT', 'SOLUSDT')
  AND instrument.status = 'ACTIVE'
  AND instrument.deleted = FALSE
  AND NOT EXISTS (
      SELECT 1
      FROM investment_ingest_cursor existing
      WHERE existing.source_id = mapping.source_id
        AND existing.instrument_id = mapping.instrument_id
        AND existing.data_type = 'BAR_INTRADAY'
        AND existing.price_type = 'MARKET'
        AND existing.interval_code = dimension.interval_code
  );

UPDATE investment_job
SET status = 'PENDING',
    attempts = 0,
    available_at = CURRENT_TIMESTAMP,
    locked_at = NULL,
    locked_by = NULL,
    claim_token = NULL,
    lease_expires_at = NULL,
    heartbeat_at = NULL,
    last_error_code = NULL,
    last_error_message = NULL,
    started_at = NULL,
    finished_at = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE workspace_id IS NULL
  AND job_type IN ('BAR_BACKFILL', 'BAR_INCREMENTAL')
  AND status = 'FAILED'
  AND last_error_code = 'UNEXPECTED_ERROR'
  AND (
      last_error_message LIKE 'Ingestion cursor does not exist for BAR_INTRADAY:%:MARKET:M5'
      OR last_error_message LIKE 'Ingestion cursor does not exist for BAR_INTRADAY:%:MARKET:M15'
      OR last_error_message LIKE 'Ingestion cursor does not exist for BAR_INTRADAY:%:MARKET:M30'
      OR last_error_message LIKE 'Ingestion cursor does not exist for BAR_INTRADAY:%:MARKET:H1'
      OR last_error_message LIKE 'Ingestion cursor does not exist for BAR_INTRADAY:%:MARKET:H4'
  );
