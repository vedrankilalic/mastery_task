package com.masteryapi.masteryapi.service;

import com.masteryapi.masteryapi.dto.DocumentsDto;
import com.masteryapi.masteryapi.entity.Documents;
import com.masteryapi.masteryapi.mapper.DocumentsMapper;
import com.masteryapi.masteryapi.types.DocumentsStatus;
import com.masteryapi.masteryapi.types.FileTypes;
import com.masteryapi.masteryapi.repository.DocumentsRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class DocumentsService {

    private final DocumentsRepository documentRepository;

    
    public List<DocumentsDto> fetchAllDocuments() {
        return documentRepository.findAll()
                .stream()
                .map(DocumentsMapper::mapToDocumentsDto)
                .toList();
    }

    public DocumentsDto fetchDocumentById(Long id) {
        Documents document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found with id: " + id));
    
        return DocumentsMapper.mapToDocumentsDto(document);
    }

    @Transactional
    public List<DocumentsDto> upload(List<MultipartFile> files) {
        List<DocumentsDto> uploaded = files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .map(this::saveDocumentMetadata)
                .toList();

        if (uploaded.isEmpty()) {
            throw new IllegalArgumentException("No valid files uploaded");
        }

        return uploaded;
    }

    private DocumentsDto saveDocumentMetadata(MultipartFile file) {
        Documents document = Documents.builder()
                .originalFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown")
                .fileType(resolveFileType(file.getOriginalFilename()))
                .status(DocumentsStatus.UPLOADED)
                .build();

        Documents saved = documentRepository.save(document);

        return DocumentsMapper.mapToDocumentsDto(saved);
    }

    private FileTypes resolveFileType(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            throw new IllegalArgumentException("Unsupported file type");
        }

        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();

        return switch (extension) {
            case "pdf" -> FileTypes.PDF;
            case "png", "jpg", "jpeg", "webp" -> FileTypes.IMAGE;
            case "csv" -> FileTypes.CSV;
            case "txt" -> FileTypes.TXT;
            default -> throw new IllegalArgumentException("Unsupported file type: " + extension);
        };
    }
}