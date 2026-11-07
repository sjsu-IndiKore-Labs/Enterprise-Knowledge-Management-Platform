package backend.knowledge_api_service;

import backend.knowledge_api_service.config.AwsConfig;
import backend.knowledge_api_service.config.JacksonConfig;
import backend.knowledge_api_service.config.OpenApiConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeApiServiceApplicationTests {

	@Test
	void contextAndConfigurationsLoad() throws Exception {
		JacksonConfig jacksonConfig = new JacksonConfig();
		ObjectMapper mapper = jacksonConfig.objectMapper();
		assertThat(mapper).isNotNull();

		String json = mapper.writeValueAsString(Map.of("createdAt", Instant.parse("2026-11-06T12:00:00Z")));
		assertThat(json).contains("2026-11-06T12:00:00Z");

		OpenApiConfig openApiConfig = new OpenApiConfig();
		assertThat(openApiConfig.enterpriseKnowledgeOpenAPI()).isNotNull();

		AwsConfig awsConfig = new AwsConfig();
		assertThat(awsConfig).isNotNull();
	}

}
