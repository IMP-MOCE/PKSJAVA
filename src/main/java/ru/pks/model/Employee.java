package ru.pks.model;

// сотрудник = одна строка из таблицы employees,
public record Employee(long id, String name, String email) { }
