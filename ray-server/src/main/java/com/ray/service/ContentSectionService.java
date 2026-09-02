package com.ray.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ray.entity.ContentSection;
import com.ray.vo.SectionDetailVO;
import com.ray.vo.SectionVO;
import java.util.List;

/** 提供官方内容分区的查询与关注能力。 */
public interface ContentSectionService extends IService<ContentSection> {
    /** 查询启用分区，可按当前用户关注关系过滤。 */
    List<SectionVO> listEnabledSections(boolean followedOnly);

    /** 查询一个启用分区的完整资料。 */
    SectionDetailVO getEnabledSection(Long sectionId);

    /** 幂等关注一个启用分区。 */
    void follow(Long sectionId);

    /** 幂等取消关注一个启用分区。 */
    void unfollow(Long sectionId);
}
