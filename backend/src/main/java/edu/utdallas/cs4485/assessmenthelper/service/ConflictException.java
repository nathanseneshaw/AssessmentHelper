package edu.utdallas.cs4485.assessmenthelper.service;

/** The request is valid but not allowed in the current state, for example confirming an expired request. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
