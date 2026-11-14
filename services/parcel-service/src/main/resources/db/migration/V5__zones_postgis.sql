-- M8: delivery zones as real PostGIS polygons, versioned pricing rules and a seed that covers the island.
-- PostGIS lives in the public schema (installed by the postgis image), so every PostGIS name is schema-qualified:
-- Flyway runs this file with search_path set to "parcel" only.

-- 1. Zones: a real geometry column next to the text column parcel-service already maps (boundary_wkt).
ALTER TABLE parcel.zones
    ADD COLUMN IF NOT EXISTS boundary public.geometry(Polygon, 4326),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- Keeps boundary and boundary_wkt in step, whichever one a writer sets.
CREATE OR REPLACE FUNCTION parcel.sync_zone_boundary() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.boundary IS NOT NULL THEN
            NEW.boundary_wkt := public.ST_AsText(NEW.boundary);
        ELSIF NEW.boundary_wkt IS NOT NULL THEN
            NEW.boundary := public.ST_SetSRID(public.ST_GeomFromText(NEW.boundary_wkt), 4326);
        END IF;
    ELSIF NEW.boundary IS DISTINCT FROM OLD.boundary THEN
        NEW.boundary_wkt := CASE WHEN NEW.boundary IS NULL THEN NULL ELSE public.ST_AsText(NEW.boundary) END;
    ELSIF NEW.boundary_wkt IS DISTINCT FROM OLD.boundary_wkt THEN
        NEW.boundary := CASE WHEN NEW.boundary_wkt IS NULL THEN NULL
                             ELSE public.ST_SetSRID(public.ST_GeomFromText(NEW.boundary_wkt), 4326) END;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS zones_sync_boundary ON parcel.zones;
CREATE TRIGGER zones_sync_boundary
    BEFORE INSERT OR UPDATE ON parcel.zones
    FOR EACH ROW EXECUTE FUNCTION parcel.sync_zone_boundary();

UPDATE parcel.zones
SET boundary = public.ST_SetSRID(public.ST_GeomFromText(boundary_wkt), 4326)
WHERE boundary IS NULL AND boundary_wkt IS NOT NULL;

CREATE INDEX IF NOT EXISTS zones_boundary_gist ON parcel.zones USING GIST (boundary);
CREATE UNIQUE INDEX IF NOT EXISTS zones_name_lower_uq ON parcel.zones (lower(name));

-- 2. Pricing rules: a rule is never edited; a new row with a later valid_from replaces it.
ALTER TABLE parcel.pricing_rules
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE parcel.pricing_rules
    ADD CONSTRAINT pricing_rules_fees_non_negative CHECK (base_fee >= 0 AND per_kg_fee >= 0),
    ADD CONSTRAINT pricing_rules_size_multiplier_positive CHECK (size_multiplier > 0);

CREATE INDEX IF NOT EXISTS pricing_rules_zone_valid_from_idx
    ON parcel.pricing_rules (zone_id, valid_from DESC, created_at DESC);

-- 3. Seed: eleven pricing zones that tile Sri Lanka without overlapping (shared edges only), so every city the
-- booking form, the AI assistant and the demo store offer falls in exactly one zone. The first three keep the
-- ids parcel-service V2 seeded, because scripts/seed-demo.mjs uses them.
UPDATE parcel.zones SET name = 'Colombo',
    boundary_wkt = 'POLYGON((79.5 6.7, 80.1 6.7, 80.1 7.0, 79.5 7.0, 79.5 6.7))'
WHERE id = '11111111-1111-1111-1111-111111111101';
UPDATE parcel.zones SET name = 'Kandy',
    boundary_wkt = 'POLYGON((80.5 6.85, 80.95 6.85, 80.95 7.3, 81.1 7.3, 81.1 7.75, 80.5 7.75, 80.5 6.85))'
WHERE id = '11111111-1111-1111-1111-111111111102';
UPDATE parcel.zones SET name = 'Galle',
    boundary_wkt = 'POLYGON((79.9 5.8, 82.0 5.8, 82.0 6.6, 81.1 6.6, 81.1 6.4, 79.9 6.4, 79.9 5.8))'
WHERE id = '11111111-1111-1111-1111-111111111103';

INSERT INTO parcel.zones (id, name, boundary_wkt, active)
VALUES ('11111111-1111-1111-1111-111111111104', 'Gampaha',
        'POLYGON((79.5 7.0, 80.1 7.0, 80.1 7.3, 79.5 7.3, 79.5 7.0))', true),
       ('11111111-1111-1111-1111-111111111105', 'Kalutara',
        'POLYGON((79.5 6.4, 80.1 6.4, 80.1 6.7, 79.5 6.7, 79.5 6.4))', true),
       ('11111111-1111-1111-1111-111111111106', 'Kurunegala',
        'POLYGON((79.5 7.3, 80.5 7.3, 80.5 7.75, 80.1 7.75, 80.1 8.55, 79.5 8.55, 79.5 7.3))', true),
       ('11111111-1111-1111-1111-111111111107', 'Jaffna',
        'POLYGON((79.5 8.55, 80.95 8.55, 80.95 9.9, 79.5 9.9, 79.5 8.55))', true),
       ('11111111-1111-1111-1111-111111111108', 'Anuradhapura',
        'POLYGON((80.1 7.75, 81.1 7.75, 81.1 8.55, 80.1 8.55, 80.1 7.75))', true),
       ('11111111-1111-1111-1111-111111111109', 'Batticaloa',
        'POLYGON((81.1 6.6, 82.0 6.6, 82.0 9.2, 80.95 9.2, 80.95 8.55, 81.1 8.55, 81.1 6.6))', true),
       ('11111111-1111-1111-1111-111111111110', 'Badulla',
        'POLYGON((80.75 6.4, 81.1 6.4, 81.1 7.3, 80.95 7.3, 80.95 6.85, 80.75 6.85, 80.75 6.4))', true),
       ('11111111-1111-1111-1111-111111111111', 'Ratnapura',
        'POLYGON((80.1 6.4, 80.75 6.4, 80.75 6.85, 80.5 6.85, 80.5 7.3, 80.1 7.3, 80.1 6.4))', true)
ON CONFLICT (id) DO NOTHING;

-- One standard (weight-tiered) rule per zone, valid from the start of 2026. Further-away zones cost more.
UPDATE parcel.pricing_rules
SET strategy = 'WEIGHT_TIERED', base_fee = 350.00, per_kg_fee = 80.00, size_multiplier = 1.20,
    valid_from = '2026-01-01T00:00:00+05:30'
WHERE id = '22222222-2222-2222-2222-222222222201';
UPDATE parcel.pricing_rules
SET strategy = 'WEIGHT_TIERED', base_fee = 450.00, per_kg_fee = 100.00, size_multiplier = 1.25,
    valid_from = '2026-01-01T00:00:00+05:30'
WHERE id = '22222222-2222-2222-2222-222222222202';
UPDATE parcel.pricing_rules
SET strategy = 'WEIGHT_TIERED', base_fee = 450.00, per_kg_fee = 100.00, size_multiplier = 1.25,
    valid_from = '2026-01-01T00:00:00+05:30'
WHERE id = '22222222-2222-2222-2222-222222222203';

INSERT INTO parcel.pricing_rules (id, zone_id, strategy, base_fee, per_kg_fee, size_multiplier, valid_from)
VALUES ('22222222-2222-2222-2222-222222222204', '11111111-1111-1111-1111-111111111104', 'WEIGHT_TIERED', 380.00, 85.00, 1.20, '2026-01-01T00:00:00+05:30'),
       ('22222222-2222-2222-2222-222222222205', '11111111-1111-1111-1111-111111111105', 'WEIGHT_TIERED', 400.00, 90.00, 1.20, '2026-01-01T00:00:00+05:30'),
       ('22222222-2222-2222-2222-222222222206', '11111111-1111-1111-1111-111111111106', 'WEIGHT_TIERED', 450.00, 95.00, 1.25, '2026-01-01T00:00:00+05:30'),
       ('22222222-2222-2222-2222-222222222207', '11111111-1111-1111-1111-111111111107', 'WEIGHT_TIERED', 650.00, 130.00, 1.30, '2026-01-01T00:00:00+05:30'),
       ('22222222-2222-2222-2222-222222222208', '11111111-1111-1111-1111-111111111108', 'WEIGHT_TIERED', 550.00, 110.00, 1.25, '2026-01-01T00:00:00+05:30'),
       ('22222222-2222-2222-2222-222222222209', '11111111-1111-1111-1111-111111111109', 'WEIGHT_TIERED', 600.00, 120.00, 1.30, '2026-01-01T00:00:00+05:30'),
       ('22222222-2222-2222-2222-222222222210', '11111111-1111-1111-1111-111111111110', 'WEIGHT_TIERED', 550.00, 115.00, 1.25, '2026-01-01T00:00:00+05:30'),
       ('22222222-2222-2222-2222-222222222211', '11111111-1111-1111-1111-111111111111', 'WEIGHT_TIERED', 500.00, 105.00, 1.25, '2026-01-01T00:00:00+05:30')
ON CONFLICT (id) DO NOTHING;
