package backend.knowledge_api_service.controller;

import backend.knowledge_api_service.dto.response.DepartmentDto;
import backend.knowledge_api_service.entity.DepartmentEntity;
import backend.knowledge_api_service.repository.DepartmentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
@Tag(name = "Meta", description = "Department reference data for dropdowns")
public class DepartmentController {

    private final DepartmentRepository departmentRepository;

    private static final List<DepartmentDto> DEFAULT_DEPARTMENTS = List.of(
            new DepartmentDto("GENERAL", "Company-wide"),
            new DepartmentDto("HR", "Human Resources"),
            new DepartmentDto("ENGINEERING", "Engineering"),
            new DepartmentDto("FINANCE", "Finance"),
            new DepartmentDto("LEGAL", "Legal"),
            new DepartmentDto("MARKETING", "Marketing")
    );

    @GetMapping
    @Operation(summary = "List all departments (for dropdowns)")
    public ResponseEntity<List<DepartmentDto>> listDepartments() {
        List<DepartmentEntity> entities = departmentRepository.findAll();
        if (entities.isEmpty()) {
            return ResponseEntity.ok(DEFAULT_DEPARTMENTS);
        }
        List<DepartmentDto> result = entities.stream()
                .map(d -> new DepartmentDto(d.getCode(), d.getName()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
}

