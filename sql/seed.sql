-- Только для пустых таблиц. Даты отсчитываются от дня загрузки.
BEGIN;
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM employees) AND NOT EXISTS (SELECT FROM bookings) THEN
        INSERT INTO employees(name, email) VALUES
            ('Иван Петров', 'petrov@company.ru'),
            ('Анна Смирнова', 'smirnova@company.ru'),
            ('Олег Иванов', 'ivanov@company.ru'),
            ('Мария Соколова', 'sokolova@company.ru'),
            ('Павел Орлов', 'orlov@company.ru');
        INSERT INTO bookings(employee_id, spot, booking_date, status)
        SELECT e.id, n::integer,
               CURRENT_DATE + CASE WHEN n % 4 = 0 THEN -1 ELSE n::integer END,
               CASE n % 4 WHEN 0 THEN 'COMPLETED' WHEN 1 THEN 'CREATED'
                          WHEN 2 THEN 'CONFIRMED' ELSE 'CANCELLED' END
        FROM generate_series(1, 10) AS n
        JOIN (SELECT id, row_number() OVER (ORDER BY id) AS position FROM employees) e
          ON e.position = (n - 1) % 5 + 1;
    END IF;
END $$;
COMMIT;
