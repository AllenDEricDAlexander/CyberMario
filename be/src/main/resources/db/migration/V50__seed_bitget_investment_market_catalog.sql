-- V50: seed the code-declared Bitget BTC/SOL USDT perpetual market-data catalog.

INSERT INTO investment_venue (code, name, timezone, status, metadata_json)
SELECT 'BITGET', 'Bitget', 'UTC', 'ACTIVE', '{"integration":"UTA_V3_PUBLIC"}'
WHERE NOT EXISTS (SELECT 1 FROM investment_venue WHERE code = 'BITGET');

INSERT INTO investment_data_source (
    venue_id, code, provider_type, api_family, product_type, capabilities_json,
    rate_limit_per_second, status, settings_json
)
SELECT id, 'BITGET', 'PUBLIC_REST', 'UTA_V3', 'USDT_FUTURES',
       '["MARKET_CANDLE","FUNDING_RATE","CURRENT_FUNDING_RATE"]',
       20, 'ACTIVE', '{"authentication":"NONE"}'
FROM investment_venue
WHERE code = 'BITGET' AND deleted = FALSE
  AND NOT EXISTS (SELECT 1 FROM investment_data_source WHERE code = 'BITGET');

INSERT INTO investment_instrument (
    venue_id, market_type, product_type, contract_type, symbol,
    base_asset, quote_asset, settlement_asset, margin_asset, status
)
SELECT venue.id, 'FUTURES', 'USDT_FUTURES', 'PERPETUAL', seed.symbol,
       seed.base_asset, 'USDT', 'USDT', 'USDT', 'ACTIVE'
FROM investment_venue venue
CROSS JOIN (VALUES ('BTCUSDT', 'BTC'), ('SOLUSDT', 'SOL')) AS seed(symbol, base_asset)
WHERE venue.code = 'BITGET' AND venue.deleted = FALSE
  AND NOT EXISTS (
      SELECT 1 FROM investment_instrument existing
      WHERE existing.venue_id = venue.id
        AND existing.product_type = 'USDT_FUTURES'
        AND existing.symbol = seed.symbol
  );

INSERT INTO investment_instrument_source (
    instrument_id, source_id, external_symbol, external_product_type, source_status, raw_metadata_json
)
SELECT instrument.id, source.id, instrument.symbol, 'USDT-FUTURES', 'ACTIVE',
       '{"seed":"CODE_SUBSCRIPTION"}'
FROM investment_instrument instrument
JOIN investment_venue venue ON venue.id = instrument.venue_id
JOIN investment_data_source source ON source.venue_id = venue.id AND source.code = 'BITGET'
WHERE venue.code = 'BITGET'
  AND instrument.product_type = 'USDT_FUTURES'
  AND instrument.symbol IN ('BTCUSDT', 'SOLUSDT')
  AND instrument.deleted = FALSE
  AND source.deleted = FALSE
  AND NOT EXISTS (
      SELECT 1 FROM investment_instrument_source existing
      WHERE existing.instrument_id = instrument.id AND existing.source_id = source.id
  );

INSERT INTO investment_ingest_cursor (
    source_id, instrument_id, data_type, price_type, interval_code, status
)
SELECT source.id, instrument.id, dimension.data_type, dimension.price_type,
       dimension.interval_code, 'IDLE'
FROM investment_instrument instrument
JOIN investment_venue venue ON venue.id = instrument.venue_id
JOIN investment_data_source source ON source.venue_id = venue.id AND source.code = 'BITGET'
CROSS JOIN (
    VALUES
        ('QUOTE', 'NONE', 'NONE'),
        ('FUNDING_RATE', 'NONE', 'NONE'),
        ('BAR_INTRADAY', 'MARKET', 'M1'),
        ('BAR_DAILY', 'MARKET', 'D1')
) AS dimension(data_type, price_type, interval_code)
WHERE venue.code = 'BITGET'
  AND instrument.product_type = 'USDT_FUTURES'
  AND instrument.symbol IN ('BTCUSDT', 'SOLUSDT')
  AND instrument.deleted = FALSE
  AND source.deleted = FALSE
  AND NOT EXISTS (
      SELECT 1 FROM investment_ingest_cursor existing
      WHERE existing.source_id = source.id
        AND existing.instrument_id = instrument.id
        AND existing.data_type = dimension.data_type
        AND existing.price_type = dimension.price_type
        AND existing.interval_code = dimension.interval_code
  );
