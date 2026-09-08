package com.ray.customer.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ray.dto.NicknameUpdateDTO;
import com.ray.dto.UserProfileUpdateDTO;
import com.ray.entity.User;
import com.ray.entity.UserProfile;
import com.ray.enums.UserGender;
import com.ray.exception.BusinessException;
import com.ray.mapper.ContentPostMapper;
import com.ray.mapper.FollowMapper;
import com.ray.mapper.UserMapper;
import com.ray.mapper.UserProfileMapper;
import com.ray.service.CityService;
import com.ray.service.CurrentUserProvider;
import com.ray.service.MediaAssetService;
import com.ray.service.impl.UserProfileServiceImpl;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.test.util.ReflectionTestUtils;

class UserProfileServiceImplTest {
    private UserMapper userMapper;
    private UserProfileMapper profileMapper;
    private FollowMapper followMapper;
    private ContentPostMapper postMapper;
    private CurrentUserProvider currentUserProvider;
    private UserProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), User.class);
        userMapper = mock(UserMapper.class);
        profileMapper = mock(UserProfileMapper.class);
        followMapper = mock(FollowMapper.class);
        postMapper = mock(ContentPostMapper.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        service = new UserProfileServiceImpl(
                userMapper,
                followMapper,
                postMapper,
                currentUserProvider,
                mock(MediaAssetService.class),
                mock(CityService.class));
        ReflectionTestUtils.setField(service, "baseMapper", profileMapper);
    }

    @Test
    void publicProfileContainsOnlyPublicFactsAndRealtimeCounts() {
        when(userMapper.selectById(7L))
                .thenReturn(new User().setId(7L).setNickName("漫游者ABCD1234").setIcon("/avatar.jpg"));
        when(profileMapper.selectById(7L))
                .thenReturn(new UserProfile()
                        .setUserId(7L)
                        .setGender(UserGender.FEMALE.name())
                        .setBirthday(LocalDate.of(2000, 1, 1))
                        .setCurrentCityCode("330100"));
        when(followMapper.selectCount(any())).thenReturn(2L, 3L);
        when(postMapper.selectCount(any())).thenReturn(4L);

        var result = service.publicProfile(7L);

        assertEquals("7", result.id());
        assertEquals(UserGender.FEMALE, result.gender());
        assertEquals(2L, result.followee());
        assertEquals(3L, result.fans());
        assertEquals(4L, result.postCount());
    }

    @Test
    void futureBirthdayIsRejectedUsingChinaCalendarDay() {
        when(currentUserProvider.requireUserId()).thenReturn(7L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.updateProfile(new UserProfileUpdateDTO(
                        UserGender.UNDISCLOSED, LocalDate.now().plusDays(1))));

        assertEquals("BIRTHDAY_IN_FUTURE", exception.code());
    }

    @Test
    void nicknameConditionalUpdatePreventsSecondChangeToday() {
        when(currentUserProvider.requireUserId()).thenReturn(7L);
        when(userMapper.selectById(7L)).thenReturn(new User().setId(7L).setNickName("当前昵称"));
        when(userMapper.update(any(), any())).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.updateNickname(new NicknameUpdateDTO("新的昵称")));

        assertEquals(429, exception.status());
        assertEquals("NICKNAME_CHANGE_LIMITED", exception.code());
    }

    @Test
    void unchangedNicknameIsRejectedWhenUserConfirmsUpdate() {
        when(currentUserProvider.requireUserId()).thenReturn(7L);
        when(userMapper.selectById(7L)).thenReturn(new User().setId(7L).setNickName("当前昵称"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.updateNickname(new NicknameUpdateDTO(" 当前昵称 ")));

        assertEquals(400, exception.status());
        assertEquals("NICKNAME_UNCHANGED", exception.code());
        assertEquals("新昵称不能与当前昵称相同", exception.getMessage());
    }
}
