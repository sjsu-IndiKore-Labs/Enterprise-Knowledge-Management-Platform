package backend.knowledge_api_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI enterpriseKnowledgeOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Enterprise Knowledge Management Platform API")
                        .description("Secure Internal Knowledge Portal Powered by Retrieval-Augmented Generation (RAG). " +
                                     "RESTful API contract for document ingestion, real-time status tracking, and pgvector hybrid search.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Ayushi (Backend & Security Lead)")
                                .email("ayushi@enterprise-knowledge-hub.local"))
                        .license(new License()
                                .name("Enterprise Confidential")
                                .url("https://enterprise-knowledge-hub.local")));
    }
}
