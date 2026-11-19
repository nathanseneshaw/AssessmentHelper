package edu.utdallas.cs4485.assessmenthelper.service;

/** A CSV import failure retains its row-level problems for the setup dialog. */
public class CycleImportException extends RuntimeException {
    private final String fileKind;
    private final ImportResult result;

    public CycleImportException(String fileKind, ImportResult result) {
        super("The " + fileKind + " CSV has problems. Nothing was imported.");
        this.fileKind = fileKind;
        this.result = result;
    }

    public String fileKind() {
        return fileKind;
    }

    public ImportResult result() {
        return result;
    }
}
