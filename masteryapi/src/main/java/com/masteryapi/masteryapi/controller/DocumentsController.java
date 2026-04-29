package com.masteryapi.masteryapi.controller;

import com.masteryapi.masteryapi.dto.DocumentsDto;
import com.masteryapi.masteryapi.service.DocumentsService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentsController {

    private final DocumentsService documentsService;

    @GetMapping("/fetch")
    public ResponseEntity<List<DocumentsDto>> fetchAllDocuments() {
        List<DocumentsDto> documents = documentsService.fetchAllDocuments();
        return ResponseEntity.ok(documents);
    }

    @GetMapping("/fetch/{id}")
public ResponseEntity<DocumentsDto> fetchDocumentById(@PathVariable Long id) {
    DocumentsDto document = documentsService.fetchDocumentById(id);
    return ResponseEntity.ok(document);
}
    
    @PostMapping("/upload")
    public ResponseEntity<List<DocumentsDto>> uploadDocuments(
            @RequestParam("files") List<MultipartFile> files
    ) {
        List<DocumentsDto> uploaded = documentsService.upload(files);
        return ResponseEntity.status(HttpStatus.CREATED).body(uploaded);
    }
}
