package backend.knowledge_api_service.model;

import java.util.List;

public enum Role {
    EMPLOYEE,
    MANAGER,
    ADMIN;

    public List<String> getPermissions() {
        return switch (this) {
            case EMPLOYEE -> List.of("DOCUMENT_READ");
            case MANAGER -> List.of("DOCUMENT_READ", "DOCUMENT_WRITE");
            case ADMIN -> List.of("DOCUMENT_READ", "DOCUMENT_WRITE", "ANALYTICS_READ", "USER_ADMIN");
        };
    }
}

