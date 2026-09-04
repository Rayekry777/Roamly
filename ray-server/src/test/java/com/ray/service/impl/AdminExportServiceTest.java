package com.ray.service.impl;

import static com.ray.constant.AdminPermissions.COMMISSION_MANAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ray.exception.BusinessException;
import com.ray.entity.FundLedgerEntry;
import com.ray.mapper.FundLedgerEntryMapper;
import com.ray.mapper.MerchantApplicationMapper;
import com.ray.mapper.OperationAuditLogMapper;
import com.ray.mapper.SettlementBatchMapper;
import com.ray.mapper.VoucherOrderMapper;
import com.ray.mapper.VoucherProductMapper;
import com.ray.mapper.VoucherRedemptionMapper;
import com.ray.mapper.VoucherRefundMapper;
import com.ray.service.AdminAuthService;
import org.junit.jupiter.api.Test;
import java.util.Collections;

class AdminExportServiceTest {
    @Test
    void unknownResourceIsRejectedBeforePermissionOrDatabaseAccess() {
        AdminAuthService admin = mock(AdminAuthService.class);
        MerchantApplicationMapper applications = mock(MerchantApplicationMapper.class);
        VoucherProductMapper products = mock(VoucherProductMapper.class);
        VoucherOrderMapper orders = mock(VoucherOrderMapper.class);
        VoucherRefundMapper refunds = mock(VoucherRefundMapper.class);
        VoucherRedemptionMapper redemptions = mock(VoucherRedemptionMapper.class);
        FundLedgerEntryMapper ledger = mock(FundLedgerEntryMapper.class);
        SettlementBatchMapper settlements = mock(SettlementBatchMapper.class);
        OperationAuditLogMapper audits = mock(OperationAuditLogMapper.class);
        AdminExportService service = new AdminExportService(
                admin, applications, products, orders, refunds, redemptions, ledger, settlements, audits);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.export("unsupported"));

        assertEquals("EXPORT_RESOURCE_INVALID", exception.code());
        verifyNoInteractions(admin, applications, products, orders, refunds, redemptions, ledger, settlements, audits);
    }

    @Test
    void ledgerExportUsesCommissionPermission() {
        AdminAuthService admin = mock(AdminAuthService.class);
        MerchantApplicationMapper applications = mock(MerchantApplicationMapper.class);
        VoucherProductMapper products = mock(VoucherProductMapper.class);
        VoucherOrderMapper orders = mock(VoucherOrderMapper.class);
        VoucherRefundMapper refunds = mock(VoucherRefundMapper.class);
        VoucherRedemptionMapper redemptions = mock(VoucherRedemptionMapper.class);
        FundLedgerEntryMapper ledger = mock(FundLedgerEntryMapper.class);
        SettlementBatchMapper settlements = mock(SettlementBatchMapper.class);
        OperationAuditLogMapper audits = mock(OperationAuditLogMapper.class);
        AdminExportService service = new AdminExportService(
                admin, applications, products, orders, refunds, redemptions, ledger, settlements, audits);

        service.export("ledger");

        verify(admin).requirePermission(COMMISSION_MANAGE);
    }

    @Test
    void exportRejectsMoreThanTenThousandRows() {
        AdminAuthService admin = mock(AdminAuthService.class);
        MerchantApplicationMapper applications = mock(MerchantApplicationMapper.class);
        VoucherProductMapper products = mock(VoucherProductMapper.class);
        VoucherOrderMapper orders = mock(VoucherOrderMapper.class);
        VoucherRefundMapper refunds = mock(VoucherRefundMapper.class);
        VoucherRedemptionMapper redemptions = mock(VoucherRedemptionMapper.class);
        FundLedgerEntryMapper ledger = mock(FundLedgerEntryMapper.class);
        SettlementBatchMapper settlements = mock(SettlementBatchMapper.class);
        OperationAuditLogMapper audits = mock(OperationAuditLogMapper.class);
        when(ledger.selectList(any())).thenReturn(Collections.nCopies(10_001, new FundLedgerEntry().setId(1L)));
        AdminExportService service = new AdminExportService(
                admin, applications, products, orders, refunds, redemptions, ledger, settlements, audits);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.export("ledger"));

        assertEquals("EXPORT_TOO_LARGE", exception.code());
    }
}
