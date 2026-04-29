package com.masteryapi.masteryapi.mapper;

import com.masteryapi.masteryapi.dto.DocumentsDto;
import com.masteryapi.masteryapi.entity.Documents;

public class DocumentsMapper {

    public static DocumentsDto mapToDocumentsDto(Documents doc) {
        return DocumentsDto.builder()
                .id(doc.getId())
                .originalFileName(doc.getOriginalFileName())
                .fileType(doc.getFileType())
                .status(doc.getStatus())
                .build();
    }
}
