package com.rental.auth;

import com.rental.auth.dto.request.*;
import com.rental.auth.dto.response.*;
import com.rental.auth.enums.Role;
import com.rental.auth.exception.ApiException;
import com.rental.auth.service.AuthService;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AuthIntegrationTest {

	@Autowired
	private AuthService auth;

	@Autowired
	private JwtDecoder decoder;

	@Test
	void customerRegistrationLoginAndPasswordRotation() {

		// 1. Tạo username duy nhất cho mỗi lần test
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);

		String username = "test_" + suffix;

		// 2. CUSTOMER đăng ký tài khoản
		UserResponse registered = auth.register(
				new RegisterRequest(username, "Customer@12345", "Test Customer", username + "@example.com", null));

		assertNotNull(registered.id());
		assertEquals(Role.CUSTOMER, registered.role());

		// 3. Đăng nhập bằng mật khẩu ban đầu
		LoginResponse loggedIn = auth.login(new LoginRequest(username, "Customer@12345"));

		assertNotNull(loggedIn.accessToken());
		assertEquals("CUSTOMER", loggedIn.role());

		// 4. Kiểm tra JWT chứa đúng user ID
		assertEquals(registered.id().toString(), decoder.decode(loggedIn.accessToken()).getSubject());

		// 5. CUSTOMER đổi mật khẩu
		auth.changePassword(registered.id(), new ChangePasswordRequest("Customer@12345", "NewPassword@12345"));

		// 6. JWT cũ phải bị vô hiệu hóa
		assertThrows(JwtException.class, () -> decoder.decode(loggedIn.accessToken()));

		// 7. Mật khẩu cũ không được đăng nhập
		assertThrows(ApiException.class, () -> auth.login(new LoginRequest(username, "Customer@12345")));

		// 8. Mật khẩu mới phải đăng nhập thành công
		LoginResponse newLogin = auth.login(new LoginRequest(username, "NewPassword@12345"));

		assertNotNull(newLogin.accessToken());

		// 9. JWT mới phải hợp lệ
		assertEquals(registered.id().toString(), decoder.decode(newLogin.accessToken()).getSubject());
	}
}
