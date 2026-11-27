package backend.knowledge_api_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "departments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepartmentEntity {

    @Id
    @Column(name = "code", length = 32, nullable = false)
    private String code;

    @Column(name = "name", length = 100, nullable = false)
    private String name;
}

