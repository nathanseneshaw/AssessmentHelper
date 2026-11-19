package edu.utdallas.cs4485.assessmenthelper.service;

import java.util.List;

public record ImportResult(boolean saved, int rowCount, List<RowError> errors) {

    public record RowError(Integer row, String column, String message) {}
}
