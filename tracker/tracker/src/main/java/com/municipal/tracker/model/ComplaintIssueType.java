package com.municipal.tracker.model;

public enum ComplaintIssueType {
    DOMESTIC_TRASH(true),
    ILLEGAL_PARKING(true),
    DAMAGED_SIGN(true),
    POTHOLE(true),

    ROAD_CRACK(false),
    GARBAGE_ACCUMULATION(false),
    WATERLOGGING(false),
    DAMAGED_STREETLIGHT(false),
    OPEN_MANHOLE(false),
    OTHER(false);

    private final boolean supportedClassification;

    ComplaintIssueType(boolean supportedClassification) {
        this.supportedClassification = supportedClassification;
    }

    public boolean isSupportedClassification() {
        return supportedClassification;
    }
}
