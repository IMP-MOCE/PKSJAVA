CREATE TABLE IF NOT EXISTS employees (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL CHECK (length(trim(name)) > 0),
    email VARCHAR(100) NOT NULL UNIQUE CHECK (length(trim(email)) > 0)
);

CREATE TABLE IF NOT EXISTS bookings (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees(id),
    spot INTEGER NOT NULL CHECK (spot BETWEEN 1 AND 50),
    booking_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED'
        CHECK (status IN ('CREATED', 'CONFIRMED', 'COMPLETED', 'CANCELLED'))
);

-- Защита от двойного бронирования при одновременной работе приложений.
CREATE UNIQUE INDEX IF NOT EXISTS booking_spot_date ON bookings(spot, booking_date)
    WHERE status IN ('CREATED', 'CONFIRMED');
CREATE UNIQUE INDEX IF NOT EXISTS booking_employee_date ON bookings(employee_id, booking_date)
    WHERE status IN ('CREATED', 'CONFIRMED');
