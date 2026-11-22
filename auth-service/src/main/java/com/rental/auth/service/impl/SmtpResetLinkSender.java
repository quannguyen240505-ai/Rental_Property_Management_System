
package com.rental.auth.service.impl;

import com.rental.auth.service.ResetLinkSender;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@Profile("!local")
public class SmtpResetLinkSender implements ResetLinkSender {

	private final JavaMailSender mail;
	private final String from;
	private final String resetPageUrl;

	public SmtpResetLinkSender(JavaMailSender mail, @Value("${app.password-reset.mail-from}") String from,
			@Value("${app.password-reset.frontend-url}") String resetPageUrl) {
		this.mail = mail;
		this.from = from;
		this.resetPageUrl = resetPageUrl;
	}

	@Override
	public void send(String recipientEmail, String rawToken) {
		SimpleMailMessage msg = new SimpleMailMessage();

		msg.setFrom(from);
		msg.setTo(recipientEmail);
		msg.setSubject("Rental Property - Reset password");

		msg.setText("Open this link to reset your password (expires in 15 minutes):\n" + resetPageUrl + "?token="
				+ URLEncoder.encode(rawToken, StandardCharsets.UTF_8));

		mail.send(msg);
	}
}
