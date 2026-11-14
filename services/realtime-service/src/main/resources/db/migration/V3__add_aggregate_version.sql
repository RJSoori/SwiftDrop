ALTER TABLE realtime.tracking_view
    ADD COLUMN aggregate_version bigint NOT NULL DEFAULT 0;
