package kr.co.jcclub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

@SpringBootApplication
public class JcclubApplication implements WebMvcConfigurer {

	public static void main(String[] args) {
		SpringApplication.run(JcclubApplication.class, args);
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		// 이미지: 파일명이 곧 버전. 바뀌면 파일명을 바꿔서 배포한다.
		registry.addResourceHandler("/assets/**")
				.addResourceLocations("classpath:/static/assets/")
				.setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());

		// ponytail: app.js는 파일명 버저닝이 없어 1시간으로 제한. 해시 파일명 도입 시 immutable로 올릴 것.
		registry.addResourceHandler("/app.js")
				.addResourceLocations("classpath:/static/app.js")
				.setCacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic());
	}

}
