package com.sua.reqbridge.analysis;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.sua.reqbridge.contract.ResourceNotFoundException;
import com.sua.reqbridge.contract.StateConflictException;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(ResourceNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	ErrorBody notFound(ResourceNotFoundException exception) {
		return new ErrorBody(new ErrorDetail("RESOURCE_NOT_FOUND", exception.getMessage(), List.of()));
	}

	@ExceptionHandler(StateConflictException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	ErrorBody conflict(StateConflictException exception) {
		return new ErrorBody(new ErrorDetail(exception.getCode(), exception.getMessage(), List.of()));
	}

	record ErrorBody(ErrorDetail error) {
	}

	record ErrorDetail(String code, String message, List<Object> fieldErrors) {
	}
}
