package edu.utdallas.cs4485.assessmenthelper.service;

public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
