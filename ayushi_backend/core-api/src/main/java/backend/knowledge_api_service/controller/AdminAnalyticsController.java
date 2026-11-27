package backend.knowledge_api_service.controller;

import backend.knowledge_api_service.dto.request.UpdateKnowledgeGapRequest;
import backend.knowledge_api_service.dto.response.*;
import backend.knowledge_api_service.model.AuditAction;
import backend.knowledge_api_service.model.Department;
import backend.knowledge_api_service.model.KnowledgeGapStatus;
import backend.knowledge_api_service.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin Analytics", description = "Analytics, knowledge gaps, and audit trail (ADMIN only)")
public class AdminAnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/analytics/summary")
    @Operation(summary = "KPI tiles for the admin dashboard (ADMIN only)")
    public ResponseEntity<AnalyticsSummaryResponse> getAnalyticsSummary(
            @RequestParam(name = "from", required = false) Instant from,
            @RequestParam(name = "to", required = false) Instant to) {
        return ResponseEntity.ok(analyticsService.getSummary(from, to));
    }

    @GetMapping("/analytics/queries-over-time")
    @Operation(summary = "Query counts bucketed by day (ADMIN only)")
    public ResponseEntity<List<TimeSeriesPointDto>> getQueriesOverTime(
            @RequestParam(name = "from", required = false) Instant from,
            @RequestParam(name = "to", required = false) Instant to,
            @RequestParam(name = "department", required = false) Department department) {
        return ResponseEntity.ok(analyticsService.getQueriesOverTime(from, to, department));
    }

    @GetMapping("/analytics/top-queries")
    @Operation(summary = "Most frequent questions (ADMIN only)")
    public ResponseEntity<List<TopQueryDto>> getTopQueries(
            @RequestParam(name = "from", required = false) Instant from,
            @RequestParam(name = "to", required = false) Instant to,
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return ResponseEntity.ok(analyticsService.getTopQueries(from, to, limit));
    }

    @GetMapping("/analytics/top-documents")
    @Operation(summary = "Most cited documents (ADMIN only)")
    public ResponseEntity<List<TopDocumentDto>> getTopDocuments(
            @RequestParam(name = "from", required = false) Instant from,
            @RequestParam(name = "to", required = false) Instant to,
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return ResponseEntity.ok(analyticsService.getTopDocuments(from, to, limit));
    }

    @GetMapping("/knowledge-gaps")
    @Operation(summary = "Unanswered / low-confidence questions grouped as knowledge gaps (ADMIN only)")
    public ResponseEntity<KnowledgeGapPageResponse> listKnowledgeGaps(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "status", required = false) KnowledgeGapStatus status,
            @RequestParam(name = "department", required = false) Department department) {
        return ResponseEntity.ok(analyticsService.listKnowledgeGaps(page, size, status, department));
    }

    @PatchMapping("/knowledge-gaps/{gapId}")
    @Operation(summary = "Change a gap's status / add notes (ADMIN only)")
    public ResponseEntity<KnowledgeGapDto> updateKnowledgeGap(
            @PathVariable("gapId") UUID gapId,
            @Valid @RequestBody UpdateKnowledgeGapRequest request) {
        return ResponseEntity.ok(analyticsService.updateKnowledgeGap(gapId, request));
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Audit trail (ADMIN only)")
    public ResponseEntity<AuditLogPageResponse> listAuditLogs(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "from", required = false) Instant from,
            @RequestParam(name = "to", required = false) Instant to,
            @RequestParam(name = "action", required = false) AuditAction action,
            @RequestParam(name = "userId", required = false) UUID userId) {
        return ResponseEntity.ok(analyticsService.listAuditLogs(page, size, from, to, action, userId));
    }
}

