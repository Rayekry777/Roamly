package com.ray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ray.entity.ContentSection;
import com.ray.entity.SectionFollow;
import com.ray.enums.EnableStatus;
import com.ray.exception.BusinessException;
import com.ray.mapper.ContentSectionMapper;
import com.ray.mapper.SectionFollowMapper;
import com.ray.service.ContentSectionService;
import com.ray.service.CurrentUserProvider;
import com.ray.utils.converter.ViewMapper;
import com.ray.vo.SectionDetailVO;
import com.ray.vo.SectionVO;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 基于分区字典和关注关系提供官方分区业务。 */
@Service
public class ContentSectionServiceImpl extends ServiceImpl<ContentSectionMapper, ContentSection>
        implements ContentSectionService {
    private final SectionFollowMapper sectionFollowMapper;
    private final CurrentUserProvider currentUserProvider;

    public ContentSectionServiceImpl(
            SectionFollowMapper sectionFollowMapper, CurrentUserProvider currentUserProvider) {
        this.sectionFollowMapper = sectionFollowMapper;
        this.currentUserProvider = currentUserProvider;
    }

    /** 查询启用分区，并一次性合并当前用户的关注状态。 */
    @Override
    public List<SectionVO> listEnabledSections(boolean followedOnly) {
        Long userId = followedOnly ? currentUserProvider.requireUserId() : currentUserProvider.optionalUserId();
        Set<Long> followedSectionIds = listFollowedSectionIds(userId);
        if (followedOnly && followedSectionIds.isEmpty()) return Collections.emptyList();

        return query()
                .eq("status", EnableStatus.ENABLED.code())
                .in(followedOnly, "id", followedSectionIds)
                .orderByAsc("sort", "id")
                .list()
                .stream()
                .map(section -> ViewMapper.toSection(section, followedSectionIds.contains(section.getId())))
                .toList();
    }

    /** 查询启用分区详情，并合并可选登录用户的关注状态。 */
    @Override
    public SectionDetailVO getEnabledSection(Long sectionId) {
        ContentSection section = requireEnabledSection(sectionId);
        Long userId = currentUserProvider.optionalUserId();
        boolean followed = userId != null && hasFollow(userId, sectionId);
        return ViewMapper.toSectionDetail(section, followed);
    }

    /** 依靠数据库唯一索引保证并发情况下的幂等关注。 */
    @Override
    @Transactional
    public void follow(Long sectionId) {
        requireEnabledSection(sectionId);
        Long userId = currentUserProvider.requireUserId();
        if (hasFollow(userId, sectionId)) return;

        try {
            sectionFollowMapper.insert(new SectionFollow().setUserId(userId).setSectionId(sectionId));
        } catch (DuplicateKeyException ignored) {
            // 并发关注由唯一索引收敛为同一最终状态。
        }
    }

    /** 删除当前用户的关注关系；关系不存在时保持成功。 */
    @Override
    @Transactional
    public void unfollow(Long sectionId) {
        requireEnabledSection(sectionId);
        Long userId = currentUserProvider.requireUserId();
        sectionFollowMapper.delete(
                new QueryWrapper<SectionFollow>().eq("user_id", userId).eq("section_id", sectionId));
    }

    private ContentSection requireEnabledSection(Long sectionId) {
        ContentSection section = getById(sectionId);
        if (section == null || !Integer.valueOf(EnableStatus.ENABLED.code()).equals(section.getStatus())) {
            throw BusinessException.notFound("SECTION_NOT_FOUND", "分区不存在或已停用");
        }
        return section;
    }

    private Set<Long> listFollowedSectionIds(Long userId) {
        if (userId == null) return Collections.emptySet();
        return sectionFollowMapper
                .selectList(new QueryWrapper<SectionFollow>()
                        .select("section_id")
                        .eq("user_id", userId))
                .stream()
                .map(SectionFollow::getSectionId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private boolean hasFollow(Long userId, Long sectionId) {
        return sectionFollowMapper.selectCount(new QueryWrapper<SectionFollow>()
                        .eq("user_id", userId)
                        .eq("section_id", sectionId))
                > 0;
    }
}
