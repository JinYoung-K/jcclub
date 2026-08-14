package kr.co.jcclub;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/quote")
public class QuoteController {

	private static final Logger log = LoggerFactory.getLogger(QuoteController.class);
	private static final long MIN_INTERVAL_MS = 60_000;

	// ponytail: 단일 인스턴스 메모리 LRU. 서버가 여러 대로 늘어나면 Redis 등 공용 저장소로 교체
	private final Map<String, Long> lastSubmit = Collections.synchronizedMap(
		new LinkedHashMap<>(16, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
				return size() > 1000;
			}
		});

	private final JavaMailSender mailSender;
	private final String to;
	private final String from;

	public QuoteController(JavaMailSender mailSender,
	                       @Value("${quote.mail.to}") String to,
	                       @Value("${quote.mail.from}") String from) {
		this.mailSender = mailSender;
		this.to = to;
		this.from = from;
	}

	public record QuoteRequest(String name, String phone, String email, String type, String pax, String detail, String website) {}

	@PostMapping
	public ResponseEntity<Map<String, String>> submit(@RequestBody QuoteRequest req, HttpServletRequest http) {
		// 허니팟: 사람에게 보이지 않는 필드. 값이 차 있으면 봇이므로 성공한 척하고 버린다
		if (!trim(req.website()).isEmpty()) {
			return ResponseEntity.ok(Map.of("status", "ok"));
		}

		String name = trim(req.name());
		String phone = trim(req.phone());
		String email = trim(req.email());
		if (name.isEmpty() || (phone.isEmpty() && email.isEmpty())) {
			return ResponseEntity.badRequest().body(Map.of("error", "invalid"));
		}

		if (isTooSoon(http.getRemoteAddr())) {
			return ResponseEntity.status(429).body(Map.of("error", "too_many"));
		}

		SimpleMailMessage msg = new SimpleMailMessage();
		msg.setFrom(from);
		msg.setTo(to);
		msg.setSubject("[JC CLUB] 견적 의뢰 - " + cut(name, 50));
		msg.setText(String.join("\n",
			"이름: " + name,
			"연락처: " + phone,
			"이메일: " + email,
			"문의 유형: " + cut(trim(req.type()), 100),
			"인원: " + cut(trim(req.pax()), 20),
			"",
			"문의 내용:",
			cut(trim(req.detail()), 5000)));
		if (!email.isEmpty()) msg.setReplyTo(email);

		try {
			mailSender.send(msg);
		} catch (MailException e) {
			log.error("견적 메일 발송 실패", e);
			return ResponseEntity.status(502).body(Map.of("error", "send_failed"));
		}
		return ResponseEntity.ok(Map.of("status", "ok"));
	}

	private boolean isTooSoon(String ip) {
		long now = System.currentTimeMillis();
		synchronized (lastSubmit) {
			Long prev = lastSubmit.get(ip);
			if (prev != null && now - prev < MIN_INTERVAL_MS) return true;
			lastSubmit.put(ip, now);
		}
		return false;
	}

	private static String trim(String s) {
		return s == null ? "" : s.trim();
	}

	private static String cut(String s, int max) {
		return s.length() > max ? s.substring(0, max) : s;
	}

}
