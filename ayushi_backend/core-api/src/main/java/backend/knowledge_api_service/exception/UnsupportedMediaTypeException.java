package backend.knowledge_api_service.exception;

import org.springframework.http.HttpStatus;

public class UnsupportedMediaTypeException extends ApiException {
    public UnsupportedMediaTypeException(String message) {
        super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", message);
    }
}

