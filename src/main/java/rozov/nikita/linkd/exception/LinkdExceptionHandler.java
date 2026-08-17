package rozov.nikita.linkd.exception;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class LinkdExceptionHandler {
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail validException(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Validation Failed");
        return problem;
    }
    @ExceptionHandler(value = EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ProblemDetail validException(EntityNotFoundException ex) {
        String detail = ex.getMessage();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, detail);
        problem.setTitle("Not Found");
        return problem;
    }
    @ExceptionHandler(value= {IllegalArgumentException.class, DataIntegrityViolationException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail handleConflictExceptions(RuntimeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Conflict");
        return problem;
    }
    @ExceptionHandler(value = MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Type Mismatch");
        return problem;
    }
    @ExceptionHandler(value = IdempotencyTimeoutException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail handleIdempotencyTimeoutException(IdempotencyTimeoutException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Timeout idempotency");
        return problem;
    }
    // works on FilterChain level
//    @ExceptionHandler(value = TooManyRequestsException.class)
//    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
//    public ProblemDetail handleTooManyRequestsException(TooManyRequestsException ex) {
//        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
//        problem.setTitle("Timeout idempotency");
//        return problem;
//    }
}
