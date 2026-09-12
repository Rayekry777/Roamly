package com.ray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ray.entity.CustomerServiceTicket;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 客服工单表的数据访问接口。 */
public interface CustomerServiceTicketMapper extends BaseMapper<CustomerServiceTicket> {
    /** 锁定工单用于串行化状态迁移。 */
    @Select("SELECT * FROM customer_service_ticket WHERE id=#{id} FOR UPDATE")
    CustomerServiceTicket selectByIdForUpdate(@Param("id") Long id);

    /** 仅允许一个客服把开放工单原子认领为处理中。 */
    @Update("UPDATE customer_service_ticket SET assignee_admin_id=#{adminId}, status='CLAIMED', "
            + "update_time=CURRENT_TIMESTAMP, version=version+1 WHERE id=#{ticketId} "
            + "AND assignee_admin_id IS NULL AND status='OPEN'")
    int claimOpenTicket(@Param("ticketId") Long ticketId, @Param("adminId") Long adminId);
}
