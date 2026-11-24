package io.github.nemios11.cvgenerator.web;

import io.github.nemios11.cvgenerator.domain.profile.InvalidProfileException;
import io.github.nemios11.cvgenerator.profile.ProfileService.ProfileNotFoundException;
import io.github.nemios11.cvgenerator.profile.ProfileService.ProfileVersionConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain and application errors to RFC 9457 problem details. Messages are
 * generic and never echo submitted values back (they may contain personal data).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidProfileException.class)
    ProblemDetail invalidProfile(InvalidProfileException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.reason());
        problem.setTitle("Invalid profile");
        problem.setProperty("field", e.field());
        return problem;
    }

    @ExceptionHandler(ProfileNotFoundException.class)
    ProblemDetail notFound(ProfileNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No profile exists yet.");
        problem.setTitle("Profile not found");
        return problem;
    }

    @ExceptionHandler(ProfileVersionConflictException.class)
    ProblemDetail conflict(ProfileVersionConflictException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.PRECONDITION_FAILED,
                "The profile was changed by another request. Reload it and try again.");
        problem.setTitle("Version conflict");
        return problem;
    }
}
