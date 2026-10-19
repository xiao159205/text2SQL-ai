package com.xym.antifraud.service;

public interface KnowledgeRetriever {

    KnowledgeRetrieval retrieve(String question, String database);

    default String retrieveContext(String question, String database) {
        return retrieve(question, database).context();
    }
}
