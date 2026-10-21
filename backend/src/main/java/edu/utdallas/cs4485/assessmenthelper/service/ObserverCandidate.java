package edu.utdallas.cs4485.assessmenthelper.service;

/**
 * A professor who could observe an observee's section. {@code courseLevel} is the highest course level (such as 4000)
 * that the professor teaches in the observee's department and semester, among the levels that qualify them.
 */
public record ObserverCandidate(
        Integer professorId, String firstName, String lastName, String email, String departmentCode, int courseLevel) {}
