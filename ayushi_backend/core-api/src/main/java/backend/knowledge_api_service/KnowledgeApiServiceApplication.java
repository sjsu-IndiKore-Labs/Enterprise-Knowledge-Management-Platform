package backend.knowledge_api_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KnowledgeApiServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(KnowledgeApiServiceApplication.class, args);
	}

}
