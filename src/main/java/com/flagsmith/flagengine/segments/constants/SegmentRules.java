package com.flagsmith.flagengine.segments.constants;

public enum SegmentRules {
    ALL_RULE("ALL"), ANY_RULE("ANY"), NONE_RULE("NONE");

    private String rule;
    private String getRule() {
        return rule;
    }

    private SegmentRules(String rule) {
        this.rule = rule;
    }
}
