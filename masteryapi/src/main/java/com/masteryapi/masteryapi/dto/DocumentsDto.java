package com.masteryapi.masteryapi.dto;

import com.masteryapi.masteryapi.types.DocumentsStatus;
import com.masteryapi.masteryapi.types.FileTypes;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class DocumentsDto {

    private Long id;
    private String originalFileName;
    private FileTypes fileType;
    private DocumentsStatus status;
}