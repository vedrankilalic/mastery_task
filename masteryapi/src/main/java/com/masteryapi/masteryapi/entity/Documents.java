package com.masteryapi.masteryapi.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.masteryapi.masteryapi.types.DocumentsStatus;
import com.masteryapi.masteryapi.types.DocumentsType;
import com.masteryapi.masteryapi.types.FileTypes;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Documents {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_file_name")
    private String originalFileName;

    @Column(name = "stored_file_path")
    private String storedFilePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type")
    private FileTypes fileType;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type")
    private DocumentsType documentType;

    @Column(name = "supplier_name")
    private String supplierName;

    @Column(name = "document_number")
    private String documentNumber;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "currency_code")
    private String currencyCode;

    @Column(name = "subtotal")
    private BigDecimal subtotal;

    @Column(name = "tax_amount")
    private BigDecimal taxAmount;

    @Column(name = "discount_amount")
    private BigDecimal discountAmount;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private DocumentsStatus status;

    @Column(name = "source_payload")
    private String sourcePayload;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder.Default
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<LineItems> lineItems = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ValidationIssues> validationIssues = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<DocumentsRevision> documentRevisions = new ArrayList<>();


    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
