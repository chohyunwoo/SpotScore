package com.spotscore.discovery;

import java.util.List;

public record RegionCrosswalkReport(
        int guCount,
        int sgisDongCount,
        int alreadyCorrectCount,
        int correctedCount,
        int newlyAddedCount,
        int looseNameMatchCount,
        int unmatchedCount,
        List<UnmatchedDong> unmatchedDongs,
        int conflictCount,
        List<ConflictEntry> conflicts,
        long elapsedMillis
) {

    public record UnmatchedDong(String guName, String sgisAdmCd, String sgisDongName) {
    }

    public record ConflictEntry(String sgisAdmCd, String sgisDongName, String realAdongCd, String occupiedBySgisAdmCd) {
    }
}
