package com.masteryapi.masteryapi.repository;

import com.masteryapi.masteryapi.entity.Documents;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentsRepository extends JpaRepository<Documents, Long> {
}
