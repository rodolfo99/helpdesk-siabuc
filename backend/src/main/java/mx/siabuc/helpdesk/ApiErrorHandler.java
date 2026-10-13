package mx.siabuc.helpdesk;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String,String>> api(ApiException e) { return error(e.status(), e.getMessage()); }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String,String>> input(Exception e) { return error(400, "Solicitud inválida: revise campos, tipos de datos y valores permitidos."); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String,String>> integrity(DataIntegrityViolationException e) {
        return error(409, "El registro entra en conflicto con datos existentes. Revise el usuario único y los valores de tiempo.");
    }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String,String>> database(DataAccessException e) {
        log.error("Fallo de acceso a la base de datos: {}", e.getClass().getSimpleName());
        return error(503, "Base de datos no disponible. Inténtelo de nuevo.");
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,String>> unexpected(Exception e) {
        log.error("Error inesperado en la API", e);
        return error(500, "No se pudo completar la operación.");
    }
    private ResponseEntity<Map<String,String>> error(int status, String message) { return ResponseEntity.status(status).body(Map.of("message", message)); }
}
