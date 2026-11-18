package com.korur.oba.dojo.util;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.dto.PaginationDetailsResponse;
import com.korur.oba.dojo.exception.internal.infrastructure.ErrorResponse;

import java.util.List;

public final class ResponseUtil {
    private ResponseUtil() {
        throw new UnsupportedOperationException("Utility class. Cannot be instantiated.");
    }

    public static <T> ResponseEntity<CustomResponse<T>> ok(String message) {
        return ResponseEntity.status(HttpStatus.OK).body(new CustomResponse<>(true, message, null, null, null));
    }

    public static <T> ResponseEntity<CustomResponse<T>> ok(String message, T data) {
        return ResponseEntity.status(HttpStatus.OK).body(new CustomResponse<>(true, message, data, null, null));
    }

    public static <T> ResponseEntity<CustomResponse<List<T>>> ok(String message, Page<T> page) {
        PaginationDetailsResponse paginationDetails = new PaginationDetailsResponse(
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber() + 1,
                page.getSize(),
                page.isFirst(),
                page.isLast(),
                page.isEmpty()
        );
        return ResponseEntity.status(HttpStatus.OK).body(new CustomResponse<>(true, message, page.getContent(), paginationDetails, null));
    }

    public static <T> ResponseEntity<CustomResponse<T>> created(String message) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new CustomResponse<>(true, message, null, null, null));
    }

    public static <T> ResponseEntity<CustomResponse<T>> created(String message, T data) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new CustomResponse<>(true, message, data, null, null));
    }

    public static <T> ResponseEntity<CustomResponse<T>> accepted(String message) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new CustomResponse<>(true, message, null, null, null));
    }

    public static <T> ResponseEntity<CustomResponse<T>> accepted(String message, T data) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new CustomResponse<>(true, message, data, null, null));
    }

    public static <T> ResponseEntity<CustomResponse<T>> error(HttpStatus status, ErrorResponse error) {
        return ResponseEntity.status(status.value()).body(new CustomResponse<>(false, error.message(), null, null, error));
    }

    public static <T> ResponseEntity<CustomResponse<T>> error(HttpStatus status, String message, ErrorResponse error) {
        return ResponseEntity.status(status.value()).body(new CustomResponse<>(false, message, null, null, error));
    }
}
