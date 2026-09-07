package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 애플리케이션 진입점.
 *
 * <p>⚠️ {@code @EnableJpaAuditing} 을 <b>여기에</b> 둔다. 별도 {@code @Configuration} 클래스로 옮기면
 * {@code @DataJpaTest} 가 그 설정을 로드하지 않아, 테스트에서만 {@code created_at} 이 null 이 된다.
 * 운영 코드는 멀쩡한데 테스트만 깨지므로 원인을 찾기 어렵다.
 *
 * <p>{@code @EnableScheduling} 은 Phase 12에서 고아 첨부 파일 정리 배치
 * ({@code OrphanAttachmentCleaner})를 위해 추가했다. **EC2 단일 인스턴스 전제**다 — 인스턴스를
 * 다중화하면 배치가 중복 실행되어 같은 파일을 여러 번 지우려 시도한다(멱등이라 오류는 아니지만
 * 낭비다). 다중화 시점에는 분산 락이나 별도 배치 인스턴스 분리를 검토한다.
 */
@EnableJpaAuditing
@EnableScheduling
@SpringBootApplication
public class TodoBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(TodoBackendApplication.class, args);
	}

}
