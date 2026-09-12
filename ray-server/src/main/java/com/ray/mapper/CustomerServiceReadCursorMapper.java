package com.ray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ray.entity.CustomerServiceReadCursor;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/** 客服已读游标的数据访问接口。 */
public interface CustomerServiceReadCursorMapper extends BaseMapper<CustomerServiceReadCursor> {
    /** 幂等推进已读游标，较旧的并发请求不能让游标倒退。 */
    @Insert("INSERT INTO customer_service_read_cursor "
            + "(id,ticket_id,reader_type,reader_id,last_read_message_id) "
            + "VALUES(#{id},#{ticketId},#{readerType},#{readerId},#{messageId}) "
            + "ON DUPLICATE KEY UPDATE last_read_message_id=GREATEST(last_read_message_id,VALUES(last_read_message_id)), "
            + "update_time=CURRENT_TIMESTAMP")
    int advance(@Param("id") Long id, @Param("ticketId") Long ticketId,
            @Param("readerType") String readerType, @Param("readerId") Long readerId,
            @Param("messageId") Long messageId);
}
