package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * 애플리케이션 진입점.
 *
 * <p>⚠️ {@code @EnableJpaAuditing} 을 <b>여기에</b> 둔다. 별도 {@code @Configuration} 클래스로 옮기면
 * {@code @DataJpaTest} 가 그 설정을 로드하지 않아, 테스트에서만 {@code created_at} 이 null 이 된다.
 * 운영 코드는 멀쩡한데 테스트만 깨지므로 원인을 찾기 어렵다.
 */
@EnableJpaAuditing
@SpringBootApplication
public class TodoBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(TodoBackendApplication.class, args);
	}

}
