package com.ray.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ray.exception.BusinessException;
import com.ray.mapper.VoucherOrderMapper;
import com.ray.service.MerchantAuthService;
import org.junit.jupiter.api.Test;

class MerchantOrderServiceImplTest {
    @Test
    void permissionDeniedBeforeMerchantOrderQuery() {
        VoucherOrderMapper orders = mock(VoucherOrderMapper.class);
        MerchantAuthService merchant = mock(MerchantAuthService.class);
        doThrow(BusinessException.forbidden("MERCHANT_FORBIDDEN", "当前角色无权执行该操作"))
                .when(merchant)
                .requirePermission(MerchantPermissionCatalog.ORDER_READ);

        MerchantOrderServiceImpl service = new MerchantOrderServiceImpl(orders, merchant);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.list(null, 1, 20));

        assertEquals("MERCHANT_FORBIDDEN", exception.code());
        verify(merchant).requirePermission(MerchantPermissionCatalog.ORDER_READ);
        verifyNoInteractions(orders);
    }
}
