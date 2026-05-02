package com.masteryapi.masteryapi.dto;

import com.masteryapi.masteryapi.types.IssueTypes;
import com.masteryapi.masteryapi.types.IssuesSeverity;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ValidationIssuesDto {

    private Long id;
    private IssueTypes issueType;
    private String fieldName;
    private String message;
    private IssuesSeverity severity;
    private Boolean isResolved;
}
