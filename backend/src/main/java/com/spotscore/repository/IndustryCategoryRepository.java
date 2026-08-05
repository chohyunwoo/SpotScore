package com.spotscore.repository;

import com.spotscore.domain.IndustryCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IndustryCategoryRepository extends JpaRepository<IndustryCategory, String> {

    List<IndustryCategory> findByFeaturedTrue();
}
