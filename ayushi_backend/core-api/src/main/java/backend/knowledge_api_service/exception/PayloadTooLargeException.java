package backend.knowledge_api_service.exception;

import org.springframework.http.HttpStatus;

public class PayloadTooLargeException extends ApiException {
    public PayloadTooLargeException(String message) {
        super(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", message);
    }
}

