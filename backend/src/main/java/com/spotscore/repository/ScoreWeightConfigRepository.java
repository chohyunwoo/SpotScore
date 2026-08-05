package com.spotscore.repository;

import com.spotscore.domain.ScoreWeightConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ScoreWeightConfigRepository extends JpaRepository<ScoreWeightConfig, Long> {

    Optional<ScoreWeightConfig> findByWeightKey(String weightKey);
}
