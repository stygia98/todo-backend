package com.example.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI(SpringDoc) 설정.
 *
 * <p>경로를 permitAll로 열어두는 것만으로는 부족하다. Authorize 버튼이 없으면
 * Phase 3 이후 보호된 API 호출이 전부 401이 되어, "Swagger에서 전체 API 확인"이
 * 형식적으로만 통과한다. 그래서 bearerAuth 보안 스킴을 전역으로 등록한다.
 *
 * <p>Swagger UI: http://localhost:8080/swagger-ui/index.html
 */
@Configuration
@SecurityScheme(
        name = OpenApiConfig.SECURITY_SCHEME_NAME,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {

    /** Authorize 버튼이 참조하는 보안 스킴 이름. */
    public static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI todoOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Todo List API")
                        .description("개인용 Todo List 서비스 REST API")
                        .version("v1"))
                // 전역 적용. 컨트롤러마다 @SecurityRequirement를 붙이지 않아도 된다.
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}
