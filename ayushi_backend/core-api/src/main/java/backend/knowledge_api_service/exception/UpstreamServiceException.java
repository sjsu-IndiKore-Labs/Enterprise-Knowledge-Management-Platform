package backend.knowledge_api_service.exception;

import org.springframework.http.HttpStatus;

public class UpstreamServiceException extends ApiException {
    public UpstreamServiceException(String message) {
        super(HttpStatus.BAD_GATEWAY, "UPSTREAM_UNAVAILABLE", message);
    }
}

