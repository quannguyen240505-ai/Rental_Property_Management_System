package com.rental.auth;

import com.rental.auth.dto.request.*;
import com.rental.auth.dto.response.*;
import com.rental.auth.enums.*;
import com.rental.auth.exception.ApiException;
import com.rental.auth.repository.UserRepository;
import com.rental.auth.service.*;

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
class UserManagementIntegrationTest {

	@Autowired
	private AuthService authService;

	@Autowired
	private UserManagementService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtDecoder jwtDecoder;

	@Test
	void ownerCanCreateLockAndUnlockStaff() {

		String username = "staff_" + UUID.randomUUID().toString().substring(0, 8);

		// 1. OWNER tạo tài khoản STAFF
		UserResponse staff = userService.create(new CreateUserRequest(username, "Staff@123456", "Test Staff",
				username + "@example.com", "0901234567", Role.STAFF));

		assertNotNull(staff.id());
		assertEquals(Role.STAFF, staff.role());
		assertEquals(UserStatus.ACTIVE, staff.status());

		// 2. STAFF đăng nhập
		LoginResponse login = authService.login(new LoginRequest(username, "Staff@123456"));

		assertNotNull(login.accessToken());
		assertEquals("STAFF", login.role());

		// 3. OWNER khóa tài khoản STAFF
		UserResponse locked = userService.setStatus(staff.id(), new UserStatusRequest(UserStatus.LOCKED));

		assertEquals(UserStatus.LOCKED, locked.status());

		// 4. JWT đã cấp trước đó không còn hợp lệ
		assertThrows(JwtException.class, () -> jwtDecoder.decode(login.accessToken()));

		// 5. STAFF bị khóa không thể đăng nhập
		assertThrows(ApiException.class, () -> authService.login(new LoginRequest(username, "Staff@123456")));

		// 6. OWNER mở khóa tài khoản
		UserResponse unlocked = userService.setStatus(staff.id(), new UserStatusRequest(UserStatus.ACTIVE));

		assertEquals(UserStatus.ACTIVE, unlocked.status());

		// 7. STAFF đăng nhập lại thành công
		LoginResponse newLogin = authService.login(new LoginRequest(username, "Staff@123456"));

		assertNotNull(newLogin.accessToken());
		assertNotNull(jwtDecoder.decode(newLogin.accessToken()));
	}

	@Test
	void tenantPromotionRequiresRentalVerification() {

		String username = "customer_" + UUID.randomUUID().toString().substring(0, 8);

		// 1. CUSTOMER tự đăng ký
		UserResponse customer = authService.register(
				new RegisterRequest(username, "Customer@12345", "Test Customer", username + "@example.com", null));

		UUID ownerId = userRepository.findByUsernameIgnoreCase("owner").orElseThrow().getId();

		// 2. Không cho cấp TENANT khi chưa xác minh
		// hợp đồng qua Rental Service
		assertThrows(ApiException.class, () -> userService.setRole(ownerId, customer.id(),
				new RoleChangeRequest(Role.TENANT, "Upgrade to tenant", "CONTRACT-001", true)));

		// 3. Vai trò vẫn là CUSTOMER
		assertEquals(Role.CUSTOMER, userService.get(customer.id()).role());
	}

	@Test
	void ownerAccountCannotBeLocked() {

		UUID ownerId = userRepository.findByUsernameIgnoreCase("owner").orElseThrow().getId();

		assertThrows(ApiException.class,
				() -> userService.setStatus(ownerId, new UserStatusRequest(UserStatus.LOCKED)));
	}

	@Test
	void ownerCanUpdateStaffInformation() {

		String username = "staff_" + UUID.randomUUID().toString().substring(0, 8);

		// 1. Tạo STAFF
		UserResponse staff = userService.create(new CreateUserRequest(username, "Staff@123456", "Original Staff",
				username + "@example.com", "0901234567", Role.STAFF));

		// 2. Cập nhật thông tin
		UserResponse updated = userService.update(staff.id(),
				new UpdateUserRequest("Updated Staff", username + "@example.com", "0987654321"));

		// 3. Kiểm tra dữ liệu
		assertEquals("Updated Staff", updated.fullName());
		assertEquals("0987654321", updated.phone());

		// 4. Kiểm tra dữ liệu được lưu trong MySQL
		UserResponse fromDatabase = userService.get(staff.id());

		assertEquals("Updated Staff", fromDatabase.fullName());
		assertEquals("0987654321", fromDatabase.phone());
	}

	@Test
	void ownerCanPromoteAndDemoteStaff() {

		String username = "customer_" + UUID.randomUUID().toString().substring(0, 8);

		// 1. CUSTOMER đăng ký tài khoản
		UserResponse customer = authService.register(
				new RegisterRequest(username, "Customer@12345", "Customer Test", username + "@example.com", null));

		UUID ownerId = userRepository.findByUsernameIgnoreCase("owner").orElseThrow().getId();

		// 2. CUSTOMER đăng nhập
		String oldToken = authService.login(new LoginRequest(username, "Customer@12345")).accessToken();

		// 3. OWNER cấp quyền STAFF
		UserResponse staff = userService.setRole(ownerId, customer.id(),
				new RoleChangeRequest(Role.STAFF, "Appointed as staff", null, false));

		assertEquals(Role.STAFF, staff.role());

		// 4. JWT cũ không còn hợp lệ
		assertThrows(JwtException.class, () -> jwtDecoder.decode(oldToken));

		// 5. OWNER thu hồi quyền STAFF
		UserResponse reverted = userService.setRole(ownerId, customer.id(),
				new RoleChangeRequest(Role.CUSTOMER, "Staff assignment ended", null, false));

		assertEquals(Role.CUSTOMER, reverted.role());

		// 6. Kiểm tra lịch sử thay đổi quyền
		var history = userService.roleHistory(customer.id());

		assertEquals(2, history.size());
		assertEquals(Role.CUSTOMER, history.get(0).newRole());
		assertEquals(Role.STAFF, history.get(1).newRole());
	}

}
