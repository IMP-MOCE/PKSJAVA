package ru.pks.exception;

// своё исключение для любых проблем с бд
// SQLException проверяемое, его пришлось бы везде пробрасывать через throws, а это RuntimeException, оно летит само
// внутри message лежит уже понятный человеку текст, а оригинальная ошибка сохраняется в cause для логов
public class DataAccessException extends RuntimeException {
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
