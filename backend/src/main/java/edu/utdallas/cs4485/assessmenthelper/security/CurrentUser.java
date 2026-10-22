package edu.utdallas.cs4485.assessmenthelper.security;

import java.util.List;

public record CurrentUser(
        Integer userId, Integer professorId, Integer schoolId, String name, String email, List<Role> roles) {}
