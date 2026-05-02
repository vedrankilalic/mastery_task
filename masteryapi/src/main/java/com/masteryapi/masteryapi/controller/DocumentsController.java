package com.masteryapi.masteryapi.controller;

import com.masteryapi.masteryapi.dto.DocumentsDto;
import com.masteryapi.masteryapi.dto.UpdateDocumentRequestDto;
import com.masteryapi.masteryapi.service.DocumentsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Upload, review and manage processed documents")
public class DocumentsController {

    private final DocumentsService documentsService;

    @GetMapping("/fetch")
    @Operation(summary = "List all documents", description = "Returns all previously uploaded/processed documents.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Documents returned successfully",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = DocumentsDto.class))))
    })
    public ResponseEntity<List<DocumentsDto>> fetchAllDocuments() {
        List<DocumentsDto> documents = documentsService.fetchAllDocuments();
        return ResponseEntity.ok(documents);
    }

    @GetMapping("/fetch/{id}")
    @Operation(summary = "Get document details", description = "Returns extracted data, line items and validation issues for one document.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Document found",
                    content = @Content(schema = @Schema(implementation = DocumentsDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid id or document not found")
    })
    public ResponseEntity<DocumentsDto> fetchDocumentById(
            @Parameter(description = "Document id", example = "1") @PathVariable Long id
    ) {
        DocumentsDto document = documentsService.fetchDocumentById(id);
        return ResponseEntity.ok(document);
    }
    
    @PostMapping("/upload")
    @Operation(summary = "Upload one or more documents", description = "Accepts multipart files and starts parsing/validation.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Files uploaded and processed",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = DocumentsDto.class)))),
            @ApiResponse(responseCode = "400", description = "No valid files or unsupported type")
    })
    public ResponseEntity<List<DocumentsDto>> uploadDocuments(
            @Parameter(description = "Files to upload (PDF, CSV, TXT, PNG, JPG, WEBP)")
            @RequestParam("files") List<MultipartFile> files
    ) {
        List<DocumentsDto> uploaded = documentsService.upload(files);
        return ResponseEntity.status(HttpStatus.CREATED).body(uploaded);
    }

    @PutMapping("/update/{id}")
    @Operation(summary = "Update document after manual review", description = "Saves corrected fields/line items and re-runs validation.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Document updated",
                    content = @Content(schema = @Schema(implementation = DocumentsDto.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed or document not found")
    })
    public ResponseEntity<DocumentsDto> updateDocument(
            @Parameter(description = "Document id", example = "1") @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Corrected document fields from review UI",
                    required = true,
                    content = @Content(schema = @Schema(implementation = UpdateDocumentRequestDto.class))
            )
            @RequestBody UpdateDocumentRequestDto request
    ) {
        return ResponseEntity.ok(documentsService.updateDocument(id, request));
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "Delete a document", description = "Deletes one document and its related child records.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Document deleted"),
            @ApiResponse(responseCode = "400", description = "Document not found")
    })
    public ResponseEntity<Void> deleteDocument(
            @Parameter(description = "Document id", example = "1") @PathVariable Long id
    ) {
        documentsService.deleteDocumentById(id);
        return ResponseEntity.noContent().build();
    }
}
