package com.girish.ai.service;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RagService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final StringRedisTemplate redisTemplate;

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are an Enterprise AI Assistant. Use ONLY the following contextual information
            retrieved from company documents to answer the user query. If the answer cannot
            be deduced from the context, state that you do not have sufficient information.
            
            CONTEXT:
            {context}
            """;

    public RagService(ChatClient chatClient, VectorStore vectorStore, StringRedisTemplate redisTemplate) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.redisTemplate = redisTemplate;
    }

    public String askWithRag(String query) {
        String cacheKey = "rag_cache:" + query.trim().toLowerCase();
        String cachedResponse = redisTemplate.opsForValue().get(cacheKey);
        if (cachedResponse != null) {
            return "[CACHED] " + cachedResponse;
        }

        // 1. Retrieve top-k relevant documents from pgvector store
        List<Document> similarDocuments = vectorStore.similaritySearch(
                SearchRequest.query(query).withTopK(4).withSimilarityThreshold(0.7)
        );

        String context = similarDocuments.stream()
                .map(Document::getContent)
                .collect(Collectors.joining("\n---\n"));

        // 2. Build contextual prompt with retrieved knowledge
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(SYSTEM_PROMPT_TEMPLATE);
        Prompt prompt = systemPromptTemplate.create(Map.of("context", context));

        // 3. Invoke LLM with augmented context
        String llmResponse = chatClient.call(prompt.getContents() + "\nUser Query: " + query);

        // 4. Save to Redis semantic cache (TTL: 1 hour)
        redisTemplate.opsForValue().set(cacheKey, llmResponse, Duration.ofHours(1));

        return llmResponse;
    }
}
