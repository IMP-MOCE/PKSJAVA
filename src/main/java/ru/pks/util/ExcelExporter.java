package ru.pks.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import ru.pks.model.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;

public final class ExcelExporter {
    private ExcelExporter() { }

    public static void export(Path file, List<Employee> employees, List<Booking> bookings) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        try (var book = new XSSFWorkbook()) {
            Sheet people = book.createSheet("Сотрудники");
            row(people, "ID", "ФИО", "Почта");
            for (Employee e : employees) row(people, e.id(), e.name(), e.email());
            Sheet reservations = book.createSheet("Бронирования");
            row(reservations, "ID", "ID сотрудника", "Место", "Дата", "Статус");
            for (Booking b : bookings) row(reservations, b.id(), b.employeeId(), b.spot(), b.date().toString(), b.status().name());
            Font font = book.createFont();
            font.setBold(true);
            CellStyle header = book.createCellStyle();
            header.setFont(font);
            for (Sheet sheet : book) {
                sheet.createFreezePane(0, 1);
                sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, sheet.getLastRowNum(), 0, sheet.getRow(0).getLastCellNum() - 1));
                for (Cell cell : sheet.getRow(0)) {
                    cell.setCellStyle(header);
                    sheet.autoSizeColumn(cell.getColumnIndex());
                    int width = sheet.getColumnWidth(cell.getColumnIndex());
                    sheet.setColumnWidth(cell.getColumnIndex(), Math.min(width + 1024, 18000));
                }
            }
            // Временный файл сохраняет прошлый экспорт при ошибке записи.
            Path temp = Files.createTempFile(parent, "parking-", ".xlsx");
            try {
                try (var out = Files.newOutputStream(temp)) { book.write(out); }
                Files.move(temp, file.toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temp); }
        }
    }

    private static void row(Sheet sheet, Object... values) {
        Row row = sheet.createRow(sheet.getPhysicalNumberOfRows());
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            if (values[i] instanceof Number number) cell.setCellValue(number.doubleValue());
            else cell.setCellValue(values[i].toString());
        }
    }
}
