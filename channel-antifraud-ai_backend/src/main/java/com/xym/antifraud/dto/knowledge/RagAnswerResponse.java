package com.xym.antifraud.dto.knowledge;

import java.util.List;

public record RagAnswerResponse(
        String question,
        String rewrittenQuery,
        String answer,
        List<Source> sources
) {
    public record Source(String id, String title, String source, double score) { }
}
