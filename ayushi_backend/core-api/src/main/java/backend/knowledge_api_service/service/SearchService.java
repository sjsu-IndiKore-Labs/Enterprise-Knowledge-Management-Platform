package backend.knowledge_api_service.service;

import backend.knowledge_api_service.dto.request.SearchRequest;
import backend.knowledge_api_service.dto.response.SearchResponse;
import backend.knowledge_api_service.dto.response.SearchResultItem;
import backend.knowledge_api_service.model.Department;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    public SearchResponse search(SearchRequest request) {
        log.info("Executing hybrid search: query='{}', departments={}, topK={}", 
                request.getQuery(), request.getAuthorizedDepartments(), request.getTopK());

        // In Milestone 3, this will execute the native PostgreSQL pgvector cosine similarity + ts_rank query.
        // For Milestone 1 contract integration, we supply a well-formed structured citation response.
        List<SearchResultItem> sampleResults = new ArrayList<>();

        Department dept = (request.getAuthorizedDepartments() != null && !request.getAuthorizedDepartments().isEmpty())
                ? request.getAuthorizedDepartments().get(0)
                : Department.GENERAL;

        sampleResults.add(SearchResultItem.builder()
                .chunkId(UUID.randomUUID())
                .documentId(UUID.randomUUID())
                .documentTitle("Enterprise Standard Operating Procedures & Policies.pdf")
                .fileName("Enterprise_SOP_2026.pdf")
                .pageNumber(12)
                .snippet("Eligible employees may take up to 16 consecutive weeks of fully paid parental leave following the birth, adoption, or foster placement of a child. All benefits continue during this period.")
                .similarityScore(0.925)
                .department(dept)
                .build());

        sampleResults.add(SearchResultItem.builder()
                .chunkId(UUID.randomUUID())
                .documentId(UUID.randomUUID())
                .documentTitle("Employee Health, Wellness & Leave Handbook.docx")
                .fileName("Health_Leave_Handbook.docx")
                .pageNumber(4)
                .snippet("To initiate a leave request, notify HR at least 30 days in advance via the internal portal and submit standard medical certification.")
                .similarityScore(0.864)
                .department(dept)
                .build());

        return SearchResponse.builder()
                .query(request.getQuery())
                .totalResults(sampleResults.size())
                .results(sampleResults)
                .build();
    }
}
