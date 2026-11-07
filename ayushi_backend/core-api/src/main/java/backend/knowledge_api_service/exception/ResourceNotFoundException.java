package backend.knowledge_api_service.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {
    public ResourceNotFoundException(String code, String message) {
        super(HttpStatus.NOT_FOUND, code, message);
    }

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", message);
    }
}

