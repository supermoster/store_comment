package com.foodiego.service.impl;

import com.foodiego.dto.Result;
import com.foodiego.dto.UserDTO;
import com.foodiego.entity.Follow;
import com.foodiego.entity.User;
import com.foodiego.mapper.FollowMapper;
import com.foodiego.service.IUserService;
import com.foodiego.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.foodiego.utils.RedisConstants.FOLLOW_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FollowServiceImpl} — follow/unfollow and common follows.
 */
@SpringBootTest(classes = {FollowServiceImpl.class, FollowMapper.class})
@DisplayName("FollowServiceImpl Unit Tests")
class FollowServiceImplTest {

    @SpyBean
    private FollowServiceImpl followService;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private FollowMapper followMapper;

    @MockBean
    private IUserService userService;

    private SetOperations<String, String> setOps;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        setOps = mock(SetOperations.class);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOps);

        UserDTO testUser = new UserDTO();
        testUser.setId(1L);
        testUser.setNickName("follower");
        UserHolder.saveUser(testUser);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    // ────────────────────── follow() tests ──────────────────────

    @Test
    @DisplayName("Follow (isFollow=true) → saves to DB and adds to Redis Set")
    void follow_IsFollowTrue_SavesAndAddsToRedis() {
        doReturn(true).when(followService).save(any(Follow.class));

        Result result = followService.follow(2L, true);

        assertTrue(result.getSuccess());
        verify(followService).save(any(Follow.class));
        verify(setOps).add(FOLLOW_KEY + "1", "2");
    }

    @Test
    @DisplayName("Unfollow (isFollow=false) → deletes from DB and removes from Redis Set")
    void unfollow_IsFollowFalse_DeletesAndRemoves() {
        doReturn(true).when(followMapper).delete(eq(1L), eq(2L));

        Result result = followService.follow(2L, false);

        assertTrue(result.getSuccess());
        verify(followMapper).delete(1L, 2L);
        verify(setOps).remove(FOLLOW_KEY + "1", "2");
    }

    // isFollow() tests — requires MyBatis-Plus query().eq().one() chain mocking;
    // covered by integration tests instead.

    // ────────────────────── common() tests ──────────────────────

    @Test
    @DisplayName("Common follows: finds intersection via Redis SINTER and returns UserDTOs")
    void common_FindsIntersection_ReturnsUsers() {
        Set<String> commonIds = new HashSet<>(Arrays.asList("3", "5"));
        when(setOps.intersect(FOLLOW_KEY + "1", FOLLOW_KEY + "2")).thenReturn(commonIds);

        User user3 = new User();
        user3.setId(3L);
        user3.setNickName("common1");
        User user5 = new User();
        user5.setId(5L);
        user5.setNickName("common2");
        doReturn(Arrays.asList(user3, user5)).when(userService).listByIds(anyList());

        Result result = followService.common(2L);

        assertTrue(result.getSuccess());
        verify(setOps).intersect(FOLLOW_KEY + "1", FOLLOW_KEY + "2");
    }

    @Test
    @DisplayName("Common follows: no intersection → returns empty result")
    void common_NoIntersection_ReturnsEmpty() {
        when(setOps.intersect(anyString(), anyString())).thenReturn(new HashSet<>());

        Result result = followService.common(2L);

        assertTrue(result.getSuccess());
        assertNull(result.getData());
        verify(userService, never()).listByIds(anyList());
    }
}
