package edu.utdallas.cs4485.assessmenthelper.service;

import edu.utdallas.cs4485.assessmenthelper.service.ImportResult.RowError;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CsvImportService {

    private static final CSVFormat FORMAT =
            CSVFormat.DEFAULT.builder().setIgnoreEmptyLines(false).get();

    record Row(int number, Map<String, String> cells) {

        String get(String column) {
            String value = cells.get(column);
            return value == null || value.isBlank() ? null : value.strip();
        }

        String required(String column, List<RowError> errors) {
            String value = get(column);
            if (value == null) errors.add(error(column, column + " is required."));
            return value;
        }

        RowError error(String column, String message) {
            return new RowError(number, column, message);
        }
    }

    private final ProfessorCsvImporter professorImporter;
    private final SectionCsvImporter sectionImporter;
    private final TransactionTemplate transaction;

    public CsvImportService(
            ProfessorCsvImporter professorImporter,
            SectionCsvImporter sectionImporter,
            PlatformTransactionManager transactionManager) {
        this.professorImporter = professorImporter;
        this.sectionImporter = sectionImporter;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
    }

    public static String professorsTemplate() {
        return String.join(",", ProfessorCsvImporter.COLUMNS) + "\r\n";
    }

    public static String sectionsTemplate() {
        return String.join(",", SectionCsvImporter.COLUMNS) + "\r\n";
    }

    public ImportResult importProfessors(byte[] content) {
        return run(content, ProfessorCsvImporter.COLUMNS, professorImporter::importRow);
    }

    public ImportResult importSections(byte[] content) {
        return run(content, SectionCsvImporter.COLUMNS, sectionImporter::importRow);
    }

    private ImportResult run(byte[] content, List<String> columns, BiConsumer<Row, List<RowError>> importer) {
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) text = text.substring(1);
        List<CSVRecord> records;
        try (CSVParser parser = CSVParser.parse(text, FORMAT)) {
            records = parser.getRecords().stream()
                    .filter(record -> !record.stream().allMatch(String::isBlank))
                    .toList();
        } catch (IOException | UncheckedIOException e) {
            return failed("The file is not valid CSV. Check that every opening quote (\") has a closing quote.");
        }
        if (records.size() < 2) return failed("The file has no rows to import.");

        List<String> header = records.getFirst().stream().map(String::strip).toList();
        List<RowError> errors = new ArrayList<>();
        for (String column : header)
            if (!column.isEmpty() && !columns.contains(column))
                errors.add(new RowError(null, column, "Unknown column '" + column + "'."));
        for (String column : columns)
            if (!header.contains(column)) errors.add(new RowError(null, column, "Missing column '" + column + "'."));
        if (!errors.isEmpty()) return new ImportResult(false, 0, errors);

        List<CSVRecord> rows = records.subList(1, records.size());
        transaction.executeWithoutResult(status -> {
            for (CSVRecord record : rows) {
                if (record.size() == header.size()) importer.accept(row(header, record), errors);
                else errors.add(cellCountError(record, header.size()));
            }
            if (!errors.isEmpty()) status.setRollbackOnly();
        });
        return new ImportResult(errors.isEmpty(), rows.size(), errors);
    }

    private static Row row(List<String> header, CSVRecord record) {
        Map<String, String> cells = new HashMap<>();
        for (int i = 0; i < header.size(); i++) cells.put(header.get(i), record.get(i));
        return new Row(rowNumber(record), cells);
    }

    private static RowError cellCountError(CSVRecord record, int columnCount) {
        String counts = "This row has " + record.size() + " cells, but the header has " + columnCount + " columns. ";
        String hint = record.size() > columnCount
                ? "If a value contains a comma, put it in double quotes, like \"Smith, Jr.\"."
                : "Each blank cell still needs its comma.";
        return new RowError(rowNumber(record), null, counts + hint);
    }

    private static int rowNumber(CSVRecord record) {
        return Math.toIntExact(record.getRecordNumber());
    }

    private static ImportResult failed(String message) {
        return new ImportResult(false, 0, List.of(new RowError(null, null, message)));
    }
}
