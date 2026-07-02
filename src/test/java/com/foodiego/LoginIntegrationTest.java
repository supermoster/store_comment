package com.foodiego;

import com.foodiego.dto.LoginFormDTO;
import com.foodiego.dto.Result;
import com.foodiego.service.IUserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import static com.foodiego.utils.RedisConstants.LOGIN_CODE_KEY;
import static com.foodiego.utils.RedisConstants.LOGIN_USER_KEY;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for user login flow with real Redis.
 * Uses Testcontainers for real MySQL + Redis.
 */
@DisplayName("Login Integration Tests")
class LoginIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IUserService userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String TEST_PHONE = "13812345678";

    @Test
    @DisplayName("Send SMS code → code stored in Redis with TTL")
    void sendCode_StoreCodeInRedis() {
        Result result = userService.sendCode(TEST_PHONE, null);

        assertTrue(result.getSuccess());
        assertNotNull(result.getData());

        String cachedCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + TEST_PHONE);
        assertNotNull(cachedCode, "Verification code should be stored in Redis");
        assertEquals(result.getData().toString(), cachedCode,
                "Returned code should match Redis-stored code");

        Long ttl = stringRedisTemplate.getExpire(LOGIN_CODE_KEY + TEST_PHONE);
        assertTrue(ttl > 0, "Code should have a TTL set");
    }

    @Test
    @DisplayName("Login with correct code → token stored in Redis Hash")
    void login_WithCorrectCode_ReturnsToken() {
        // Send code
        Result codeResult = userService.sendCode(TEST_PHONE, null);
        String code = codeResult.getData().toString();

        // Login with correct code
        LoginFormDTO form = new LoginFormDTO();
        form.setPhone(TEST_PHONE);
        form.setCode(code);

        Result loginResult = userService.login(form, null);

        assertTrue(loginResult.getSuccess(), "Login should succeed");
        String token = (String) loginResult.getData();
        assertNotNull(token);
        assertFalse(token.isEmpty(), "Token should be a non-empty UUID");

        // Verify token stored in Redis
        String tokenKey = LOGIN_USER_KEY + token;
        Boolean hasKey = stringRedisTemplate.hasKey(tokenKey);
        assertTrue(Boolean.TRUE.equals(hasKey), "Token should be stored in Redis Hash");

        Long ttl = stringRedisTemplate.getExpire(tokenKey);
        assertTrue(ttl > 0, "Token should have TTL set");
    }

    @Test
    @DisplayName("Login with wrong code → returns error")
    void login_WithWrongCode_ReturnsError() {
        // Send code
        userService.sendCode(TEST_PHONE, null);

        // Login with WRONG code
        LoginFormDTO form = new LoginFormDTO();
        form.setPhone(TEST_PHONE);
        form.setCode("000000"); // Wrong code

        Result result = userService.login(form, null);

        assertFalse(result.getSuccess());
        assertEquals("验证码错误！", result.getErrorMsg());
    }

    @Test
    @DisplayName("Login creates new user if phone not in DB, and token is valid")
    void login_NewUser_AutoCreatesAccount() {
        String newPhone = "13999990000";

        // Send code
        Result codeResult = userService.sendCode(newPhone, null);
        String code = codeResult.getData().toString();

        // Login — should auto-create user
        LoginFormDTO form = new LoginFormDTO();
        form.setPhone(newPhone);
        form.setCode(code);

        Result result = userService.login(form, null);

        assertTrue(result.getSuccess(), "New user login should succeed");
        String token = (String) result.getData();
        assertNotNull(token);
        assertFalse(token.isEmpty());

        // Verify token exists in Redis
        String tokenKey = LOGIN_USER_KEY + token;
        Boolean hasKey = stringRedisTemplate.hasKey(tokenKey);
        assertTrue(Boolean.TRUE.equals(hasKey), "Token should be stored for new user");
    }

    @Test
    @DisplayName("Invalid phone number → returns error")
    void sendCode_InvalidPhone_ReturnsError() {
        Result result = userService.sendCode("abc", null);

        assertFalse(result.getSuccess());
        assertEquals("手机号格式错误！", result.getErrorMsg());
    }
}
