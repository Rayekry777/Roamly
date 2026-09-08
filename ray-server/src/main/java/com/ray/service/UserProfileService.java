package com.ray.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ray.dto.AvatarUpdateDTO;
import com.ray.dto.CityPreferenceUpdateDTO;
import com.ray.dto.NicknameUpdateDTO;
import com.ray.dto.UserProfileUpdateDTO;
import com.ray.entity.UserProfile;
import com.ray.vo.CurrentUserProfileVO;
import com.ray.vo.PublicUserProfileVO;

/** 消费者本人资料、公开主页与内部城市偏好能力。 */
public interface UserProfileService extends IService<UserProfile> {
    /** 查询当前消费者的完整私有资料。 */
    CurrentUserProfileVO currentProfile();

    /** 查询不包含手机号和生日的公开用户主页。 */
    PublicUserProfileVO publicProfile(Long userId);

    /** 更新当前消费者的性别和生日。 */
    CurrentUserProfileVO updateProfile(UserProfileUpdateDTO request);

    /** 按北京时间自然日限频更新昵称。 */
    CurrentUserProfileVO updateNickname(NicknameUpdateDTO request);

    /** 原子替换当前消费者头像。 */
    CurrentUserProfileVO updateAvatar(AvatarUpdateDTO request);

    /** 更新不对外展示的当前城市偏好。 */
    void updateCityPreference(CityPreferenceUpdateDTO request);

    /** 查询普通动态归属使用的内部城市编码。 */
    String currentCityCode(Long userId);
}
