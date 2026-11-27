package hu.bplusn.portal.web;

import hu.bplusn.portal.jira.JiraException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/** Egységes JSON hibaválasz: {"error": "..."}. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    public record ApiError(String error) {
    }

    @ExceptionHandler(JiraException.class)
    public ResponseEntity<ApiError> jira(JiraException exception, HttpServletRequest request) {
        if (exception.kind() == JiraException.Kind.UNAUTHORIZED) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
        }
        if (exception.kind() == JiraException.Kind.UNAVAILABLE) {
            log.warn("Jira hiba: {}", exception.getMessage(), exception.getCause());
        }
        return ResponseEntity.status(exception.kind().httpStatus()).body(new ApiError(exception.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> status(ResponseStatusException exception) {
        String reason = exception.getReason() != null ? exception.getReason() : "A kérés nem teljesíthető.";
        return ResponseEntity.status(exception.getStatusCode()).body(new ApiError(reason));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> badArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ApiError(exception.getMessage()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> unreadable(Exception exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError("Érvénytelen kérés."));
    }
}
