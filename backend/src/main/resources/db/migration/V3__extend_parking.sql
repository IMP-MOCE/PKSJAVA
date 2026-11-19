ALTER TABLE employees ADD COLUMN uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE;
ALTER TABLE employees ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE employees ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE employees ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE employees ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE bookings ADD COLUMN uuid UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE;
ALTER TABLE bookings ADD COLUMN code VARCHAR(100);
UPDATE bookings SET code = 'B-' || id;
ALTER TABLE bookings ALTER COLUMN code SET NOT NULL;
ALTER TABLE bookings ADD CONSTRAINT booking_code_unique UNIQUE (code);
ALTER TABLE bookings ADD COLUMN purpose VARCHAR(200) NOT NULL DEFAULT 'Рабочий день';
ALTER TABLE bookings ADD COLUMN guest BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS archived BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE bookings ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE bookings ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE bookings ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

DROP INDEX booking_spot_date;
DROP INDEX booking_employee_date;
CREATE UNIQUE INDEX booking_spot_date ON bookings(spot, booking_date)
    WHERE status IN ('CREATED', 'CONFIRMED') AND NOT archived;
CREATE UNIQUE INDEX booking_employee_date ON bookings(employee_id, booking_date)
    WHERE status IN ('CREATED', 'CONFIRMED') AND NOT archived;
CREATE INDEX booking_status ON bookings(status);
CREATE INDEX booking_date ON bookings(booking_date);
CREATE INDEX booking_employee ON bookings(employee_id);
CREATE INDEX booking_archive ON bookings(archived);
CREATE INDEX employee_search ON employees(lower(name), lower(email));
CREATE UNIQUE INDEX employee_email_lower ON employees(lower(email));

CREATE TABLE booking_tags (
    booking_id BIGINT NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    tag VARCHAR(50) NOT NULL,
    PRIMARY KEY (booking_id, tag)
);
CREATE INDEX booking_tag_value ON booking_tags(tag);

CREATE TABLE booking_notes (
    id UUID PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES app_user(id),
    body VARCHAR(4000) NOT NULL,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX booking_note_history ON booking_notes(booking_id, pinned DESC, created_at DESC);

-- старый клиент не передает новые поля и не увеличивает версию
CREATE FUNCTION fill_booking_code() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.code IS NULL THEN
        NEW.code = 'B-' || NEW.id;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER booking_code_default BEFORE INSERT ON bookings
    FOR EACH ROW EXECUTE FUNCTION fill_booking_code();

CREATE FUNCTION track_parking_update() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.version = OLD.version + 1;
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;
CREATE TRIGGER booking_version BEFORE UPDATE ON bookings
    FOR EACH ROW EXECUTE FUNCTION track_parking_update();
CREATE TRIGGER employee_version BEFORE UPDATE ON employees
    FOR EACH ROW EXECUTE FUNCTION track_parking_update();
