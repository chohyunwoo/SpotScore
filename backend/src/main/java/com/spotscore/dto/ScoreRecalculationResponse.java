package com.spotscore.dto;

import java.time.LocalDate;

public record ScoreRecalculationResponse(int year, LocalDate snapshotDate, int scoreCacheRowsSaved) {
}
