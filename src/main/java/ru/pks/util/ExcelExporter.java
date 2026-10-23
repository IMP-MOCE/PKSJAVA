package ru.pks.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import ru.pks.model.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;

// выгрузка в excel через библиотеку apache poi
// на выходе файл с двумя листами: сотрудники и бронирования
// экземпляры класса не нужны, поэтому конструктор приватный и всё сделано через static
public final class ExcelExporter {
    private ExcelExporter() { }

    // сохраняет переданные списки в xlsx по пути targetFile
    public static void export(Path targetFile, List<Employee> employees, List<Booking> bookings) throws IOException {
        Path targetFolder = targetFile.toAbsolutePath().getParent();
        // если папки ещё нет, создаём, иначе запись упадёт
        Files.createDirectories(targetFolder);
        try (var workbook = new XSSFWorkbook()) {
            // первая строка каждого листа это заголовки, дальше по строке на каждую запись
            Sheet employeesSheet = workbook.createSheet("Сотрудники");
            row(employeesSheet, "ID", "ФИО", "Почта");
            for (Employee employee : employees) row(employeesSheet, employee.id(), employee.name(), employee.email());
            Sheet bookingsSheet = workbook.createSheet("Бронирования");
            row(bookingsSheet, "ID", "ID сотрудника", "Место", "Дата", "Статус");
            for (Booking booking : bookings) {
                row(bookingsSheet, booking.id(), booking.employeeId(), booking.spot(), booking.date().toString(), booking.status().name());
            }

            // стиль для заголовков, просто жирный шрифт
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(boldFont);
            // дальше одинаково оформляем оба листа
            for (Sheet sheet : workbook) {
                // закрепляем первую строку, чтобы заголовки не уезжали при прокрутке
                sheet.createFreezePane(0, 1);
                // включаем автофильтр (стрелочки в заголовках) на весь диапазон с данными
                int lastColumnIndex = sheet.getRow(0).getLastCellNum() - 1;
                sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, sheet.getLastRowNum(), 0, lastColumnIndex));
                for (Cell headerCell : sheet.getRow(0)) {
                    headerCell.setCellStyle(headerStyle);
                    // подгоняем ширину колонки под содержимое, добавляем немного запаса под стрелку фильтра,
                    // но не шире 18000, чтобы длинные почты не растягивали колонку на пол-экрана
                    sheet.autoSizeColumn(headerCell.getColumnIndex());
                    int autoWidth = sheet.getColumnWidth(headerCell.getColumnIndex());
                    sheet.setColumnWidth(headerCell.getColumnIndex(), Math.min(autoWidth + 1024, 18000));
                }
            }

            // сначала пишем во временный файл и только потом подменяем им нужный
            // так если запись упадёт на середине, старый экспорт останется целым, а не битым
            Path tempFile = Files.createTempFile(targetFolder, "parking-", ".xlsx");
            try {
                try (var output = Files.newOutputStream(tempFile)) { workbook.write(output); }
                Files.move(tempFile, targetFile.toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(tempFile); }
        }
    }

    // дописывает в конец листа новую строку со значениями по ячейкам слева направо
    // числа кладём числами, чтобы в excel по ним работала сортировка, остальное строкой
    private static void row(Sheet sheet, Object... cellValues) {
        Row newRow = sheet.createRow(sheet.getPhysicalNumberOfRows());
        for (int columnIndex = 0; columnIndex < cellValues.length; columnIndex++) {
            Cell cell = newRow.createCell(columnIndex);
            if (cellValues[columnIndex] instanceof Number number) cell.setCellValue(number.doubleValue());
            else cell.setCellValue(cellValues[columnIndex].toString());
        }
    }
}
