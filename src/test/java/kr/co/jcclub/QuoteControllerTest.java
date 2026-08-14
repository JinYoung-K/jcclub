package kr.co.jcclub;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuoteControllerTest {

	@Test
	void 사용자_입력은_이스케이프된다() {
		String out = QuoteController.html("<script>alert(1)</script>", "010", "a@b.c", "", "", "\"위험\" & <b>");

		assertFalse(out.contains("<script>"), "스크립트 태그가 그대로 들어가면 안 된다");
		assertTrue(out.contains("&lt;script&gt;"));
		assertTrue(out.contains("&quot;위험&quot; &amp; &lt;b&gt;"));
	}

	@Test
	void 빈_항목은_행을_만들지_않는다() {
		String out = QuoteController.html("홍길동", "", "", "", "", "");

		assertFalse(out.contains("연락처"));
		assertFalse(out.contains("문의자에게 회신"), "이메일이 없으면 회신 버튼도 없다");
		assertTrue(out.contains("내용 없음"));
	}
}
