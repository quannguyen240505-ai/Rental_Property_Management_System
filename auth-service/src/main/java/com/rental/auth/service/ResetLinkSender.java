package com.rental.auth.service;

public interface ResetLinkSender {

	void send(String recipientEmail, String rawToken);
}
