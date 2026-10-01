package com.nexusops.notification.repository;

import com.nexusops.notification.domain.Template;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TemplateRepository extends JpaRepository<Template, String> {

    Optional<Template> findByTemplateKeyAndActiveTrue(String templateKey);
}
