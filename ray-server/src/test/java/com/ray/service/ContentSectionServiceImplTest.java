package com.ray.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ray.entity.ContentSection;
import com.ray.entity.SectionFollow;
import com.ray.exception.BusinessException;
import com.ray.mapper.ContentSectionMapper;
import com.ray.mapper.SectionFollowMapper;
import com.ray.service.impl.ContentSectionServiceImpl;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ContentSectionServiceImplTest {
    private ContentSectionMapper sectionMapper;
    private SectionFollowMapper followMapper;
    private CurrentUserProvider currentUserProvider;
    private ContentSectionServiceImpl service;

    @BeforeEach
    void setUp() {
        sectionMapper = mock(ContentSectionMapper.class);
        followMapper = mock(SectionFollowMapper.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        service = new ContentSectionServiceImpl(followMapper, currentUserProvider);
        ReflectionTestUtils.setField(service, "baseMapper", sectionMapper);
    }

    @Test
    void anonymousListDoesNotQueryFollowRelations() {
        when(currentUserProvider.optionalUserId()).thenReturn(null);
        when(sectionMapper.selectList(any())).thenReturn(List.of(enabledSection(1L)));

        assertFalse(service.listEnabledSections(false).getFirst().followedByMe());
        verify(followMapper, never()).selectList(any());
    }

    @Test
    void followedOnlyListMarksReturnedSections() {
        when(currentUserProvider.requireUserId()).thenReturn(7L);
        when(followMapper.selectList(any())).thenReturn(List.of(new SectionFollow().setSectionId(2L)));
        when(sectionMapper.selectList(any())).thenReturn(List.of(enabledSection(2L)));

        assertTrue(service.listEnabledSections(true).getFirst().followedByMe());
    }

    @Test
    void rejectsDisabledSection() {
        when(sectionMapper.selectById(1L)).thenReturn(enabledSection(1L).setStatus(0));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.getEnabledSection(1L));
        assertEquals("SECTION_NOT_FOUND", exception.code());
    }

    @Test
    void followIsIdempotentWhenRelationAlreadyExists() {
        when(sectionMapper.selectById(1L)).thenReturn(enabledSection(1L));
        when(currentUserProvider.requireUserId()).thenReturn(7L);
        when(followMapper.selectCount(any())).thenReturn(1L);

        service.follow(1L);

        verify(followMapper, never()).insert(any(SectionFollow.class));
    }

    private ContentSection enabledSection(Long id) {
        return new ContentSection()
                .setId(id)
                .setCode("ROAM_DAILY")
                .setName("漫游日常")
                .setAllowShopVisit(0)
                .setStatus(1);
    }
}
