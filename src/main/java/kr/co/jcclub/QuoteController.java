package kr.co.jcclub;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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
	private final String[] to;
	private final String from;

	public QuoteController(JavaMailSender mailSender,
	                       @Value("${quote.mail.to}") String[] to,
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

		String type = cut(trim(req.type()), 100);
		String pax = cut(trim(req.pax()), 20);
		String detail = cut(trim(req.detail()), 5000);

		try {
			MimeMessage msg = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
			helper.setFrom(from);
			helper.setTo(to);
			helper.setSubject("[JC CLUB] 견적 의뢰 - " + cut(name, 50));
			helper.setText(html(name, phone, email, type, pax, detail), true);
			if (!email.isEmpty()) helper.setReplyTo(email);
			mailSender.send(msg);
		} catch (MailException | MessagingException e) {
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

	static String html(String name, String phone, String email, String type, String pax, String detail) {
		StringBuilder rows = new StringBuilder();
		row(rows, "이름", name);
		row(rows, "연락처", phone);
		row(rows, "이메일", email);
		row(rows, "문의 유형", type);
		row(rows, "인원", pax);

		String replyBtn = email.isEmpty() ? "" :
			"<tr><td style=\"padding:8px 32px 32px\">"
				+ "<a href=\"mailto:" + esc(email) + "\" style=\"display:inline-block;padding:13px 28px;border-radius:4px;"
				+ "background:#ac3231;color:#ffffff;font-size:15px;font-weight:700;text-decoration:none\">문의자에게 회신</a>"
				+ "</td></tr>";

		return "<!DOCTYPE html><html lang=\"ko\"><body style=\"margin:0;padding:24px 12px;background:#f8f9fa\">"
			+ "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" "
			+ "style=\"max-width:600px;margin:0 auto;background:#ffffff;border-radius:6px;overflow:hidden;"
			+ "font-family:'Noto Sans KR','Apple SD Gothic Neo',sans-serif;color:#333333\">"

			+ "<tr><td style=\"padding:28px 32px;background:#001e40\">"
			+ "<div style=\"font-size:22px;font-weight:700;color:#ffffff;letter-spacing:2px\">JC CLUB</div>"
			+ "<div style=\"width:44px;height:3px;margin:10px 0 12px;background:#ac3231\"></div>"
			+ "<div style=\"font-size:15px;color:#a7c8ff\">새로운 견적 의뢰가 접수되었습니다</div>"
			+ "</td></tr>"

			+ "<tr><td style=\"padding:24px 32px 8px\">"
			+ "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"font-size:15px\">"
			+ rows
			+ "</table></td></tr>"

			+ "<tr><td style=\"padding:16px 32px 0\">"
			+ "<div style=\"padding-bottom:10px;border-bottom:1px solid #eeeeee;font-size:13px;font-weight:600;color:#001e40\">문의 내용</div>"
			+ "<div style=\"padding:16px 0;font-size:15px;line-height:1.7;white-space:pre-wrap\">"
			+ (detail.isEmpty() ? "<span style=\"color:#999999\">내용 없음</span>" : esc(detail))
			+ "</div></td></tr>"

			+ replyBtn

			+ "<tr><td style=\"padding:18px 32px;background:#f8f9fa;border-top:1px solid #eeeeee;font-size:12px;color:#888888\">"
			+ "jcclubtour.com 견적 의뢰 폼에서 자동 발송된 메일입니다."
			+ "</td></tr>"

			+ "</table></body></html>";
	}

	private static void row(StringBuilder sb, String label, String value) {
		if (value.isEmpty()) return;
		sb.append("<tr>")
			.append("<td width=\"96\" style=\"padding:9px 0;color:#888888;vertical-align:top\">").append(label).append("</td>")
			.append("<td style=\"padding:9px 0;font-weight:600\">").append(esc(value)).append("</td>")
			.append("</tr>");
	}

	private static String esc(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

}
