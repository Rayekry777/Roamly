package com.ray.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ray.dto.MerchantVoucherProductCreateDTO;
import com.ray.dto.MerchantVoucherProductOffSaleDTO;
import com.ray.dto.MerchantVoucherProductSubmitDTO;
import com.ray.dto.MerchantVoucherProductUpdateDTO;
import com.ray.entity.MerchantAccount;
import com.ray.entity.VoucherProduct;
import com.ray.enums.BusinessMediaPurpose;
import com.ray.enums.MerchantAccountStatus;
import com.ray.enums.MerchantRole;
import com.ray.enums.VoucherProductType;
import com.ray.enums.VoucherReviewStatus;
import com.ray.exception.BusinessException;
import com.ray.mapper.VoucherOrderMapper;
import com.ray.mapper.VoucherPackageItemMapper;
import com.ray.mapper.VoucherProductMapper;
import com.ray.service.BusinessMediaService;
import com.ray.service.MerchantAuditService;
import com.ray.service.MerchantAuthService;
import com.ray.vo.BusinessMediaVO;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class MerchantVoucherProductServiceImplTest {
    @Mock
    private VoucherProductMapper productMapper;

    @Mock
    private VoucherPackageItemMapper itemMapper;

    @Mock
    private VoucherOrderMapper orderMapper;

    @Mock
    private MerchantAuthService authService;

    @Mock
    private BusinessMediaService mediaService;

    @Mock
    private MerchantAuditService auditService;

    private MerchantVoucherProductServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new MerchantVoucherProductServiceImpl(
                productMapper,
                itemMapper,
                orderMapper,
                authService,
                mediaService,
                auditService,
                new ObjectMapper().findAndRegisterModules());
        when(itemMapper.selectList(any())).thenReturn(List.of());
        when(mediaService.viewsForVoucherProduct(any(), any(), any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void verifierCannotCreateVoucherDraft() {
        when(authService.requireCurrentAccount()).thenReturn(account(MerchantRole.VERIFIER));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.create(new MerchantVoucherProductCreateDTO(VoucherProductType.PACKAGE)));

        assertEquals(403, exception.status());
        assertEquals("MERCHANT_FORBIDDEN", exception.code());
        verify(productMapper, never()).insert(any(VoucherProduct.class));
    }

    @Test
    void createsTypedDraftAndWritesMerchantAudit() {
        when(authService.requireCurrentAccount()).thenReturn(account(MerchantRole.TENANT));
        ArgumentCaptor<VoucherProduct> inserted = ArgumentCaptor.forClass(VoucherProduct.class);
        when(productMapper.insert(inserted.capture())).thenAnswer(invocation -> {
            invocation.getArgument(0, VoucherProduct.class).setId(3201L);
            return 1;
        });
        when(productMapper.selectById(3201L)).thenAnswer(invocation -> inserted.getValue());

        var result = service.create(new MerchantVoucherProductCreateDTO(VoucherProductType.DISCOUNT));

        assertEquals("3201", result.id());
        assertEquals(VoucherProductType.DISCOUNT, result.productType());
        assertEquals(VoucherReviewStatus.DRAFT, result.reviewStatus());
        assertEquals(0, result.version());
        verify(auditService).record(
                1L, "MERCHANT_VOUCHER_DRAFT_CREATED", "VOUCHER_PRODUCT", "3201", "SUCCEEDED", null);
    }

    @Test
    void rejectsStaleDraftVersionBeforeChangingChildren() {
        when(authService.requireCurrentAccount()).thenReturn(account(MerchantRole.MANAGER));
        when(productMapper.selectByIdForUpdate(3101L)).thenReturn(new VoucherProduct()
                .setId(3101L)
                .setShopId(10L)
                .setProductType(VoucherProductType.PACKAGE.name())
                .setReviewStatus(VoucherReviewStatus.DRAFT.name())
                .setVersion(2));

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.update(3101L, emptyUpdate(1)));

        assertEquals("VOUCHER_PRODUCT_VERSION_CONFLICT", exception.code());
        verify(itemMapper, never()).delete(any());
        verify(mediaService, never()).syncVoucherProductReferences(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsSubmissionWhenDraftIsIncomplete() {
        when(authService.requireCurrentAccount()).thenReturn(account(MerchantRole.TENANT));
        when(productMapper.selectByIdForUpdate(3101L)).thenReturn(new VoucherProduct()
                .setId(3101L)
                .setShopId(10L)
                .setProductType(VoucherProductType.CASH.name())
                .setTitle(null)
                .setDetailMediaIdsJson("[]")
                .setUsageRulesJson("[]")
                .setExcludedDatesJson("[]")
                .setReviewStatus(VoucherReviewStatus.DRAFT.name())
                .setVersion(0));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.submit(3101L, "stage20-submit", new MerchantVoucherProductSubmitDTO(0)));

        assertEquals(400, exception.status());
        assertEquals("VOUCHER_PRODUCT_INCOMPLETE", exception.code());
        verify(productMapper, never()).update(eq(null), any());
    }

    @Test
    void submitsCompleteCashVoucherAndReturnsPendingFact() {
        when(authService.requireCurrentAccount()).thenReturn(account(MerchantRole.TENANT));
        VoucherProduct draft = completeCash(VoucherReviewStatus.DRAFT, 0);
        VoucherProduct pending = completeCash(VoucherReviewStatus.PENDING, 1)
                .setSubmissionIdempotencyKey("stage20-submit")
                .setSubmittedAt(LocalDateTime.of(2026, 9, 4, 18, 0));
        when(productMapper.selectByIdForUpdate(3102L)).thenReturn(draft);
        when(productMapper.update(eq(null), any())).thenReturn(1);
        when(productMapper.selectById(3102L)).thenReturn(pending);
        when(mediaService.viewsForVoucherProduct(1L, 10L, 3102L, 9001L, List.of()))
                .thenReturn(List.of(media(9001L)));

        var result = service.submit(
                3102L, "stage20-submit", new MerchantVoucherProductSubmitDTO(0));

        assertEquals(VoucherReviewStatus.PENDING, result.reviewStatus());
        assertEquals(1, result.version());
        verify(auditService).record(
                1L, "MERCHANT_VOUCHER_SUBMITTED", "VOUCHER_PRODUCT", "3102", "SUCCEEDED", null);
    }

    @Test
    void offSaleApprovedVoucherIsIdempotentAndReturnsOffSaleFact() {
        when(authService.requireCurrentAccount()).thenReturn(account(MerchantRole.TENANT));
        VoucherProduct product = new VoucherProduct()
                .setId(3103L)
                .setShopId(10L)
                .setProductType(VoucherProductType.CASH.name())
                .setTitle("代金券")
                .setDetailMediaIdsJson("[]")
                .setUsageRulesJson("[]")
                .setExcludedDatesJson("[]")
                .setTotalStock(10)
                .setAvailableStock(10)
                .setSoldCount(0)
                .setPurchaseLimit(1)
                .setReviewStatus(VoucherReviewStatus.APPROVED.name())
                .setSaleStatus("ON_SALE")
                .setVersion(2);
        when(productMapper.selectByIdForUpdate(3103L)).thenReturn(product);
        when(productMapper.update(eq(null), any())).thenReturn(1);
        when(productMapper.selectById(3103L)).thenAnswer(invocation -> product.setSaleStatus("OFF_SALE").setVersion(3));

        var result = service.offSale(
                3103L,
                "off-sale-key",
                new MerchantVoucherProductOffSaleDTO(2, "库存调整"));

        assertEquals("OFF_SALE", result.saleStatus().name());
        assertEquals(3, result.version());
        verify(auditService).record(
                1L, "MERCHANT_VOUCHER_OFF_SALE", "VOUCHER_PRODUCT", "3103", "SUCCEEDED", "库存调整");
    }

    private MerchantAccount account(MerchantRole role) {
        return new MerchantAccount()
                .setId(1L)
                .setShopId(10L)
                .setRole(role.name())
                .setStatus(MerchantAccountStatus.ACTIVE.name());
    }

    private MerchantVoucherProductUpdateDTO emptyUpdate(int version) {
        return new MerchantVoucherProductUpdateDTO(
                version,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                0,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                false,
                null,
                false,
                false,
                false,
                List.of());
    }

    private VoucherProduct completeCash(VoucherReviewStatus status, int version) {
        return new VoucherProduct()
                .setId(3102L)
                .setShopId(10L)
                .setProductType(VoucherProductType.CASH.name())
                .setTitle("50元代金券")
                .setCoverMediaId(9001L)
                .setDetailMediaIdsJson("[]")
                .setPriceAmount(4200L)
                .setMarketAmount(5000L)
                .setFaceValueAmount(5000L)
                .setMinimumSpendAmount(5000L)
                .setTotalStock(100)
                .setAvailableStock(100)
                .setSoldCount(0)
                .setPurchaseLimit(1)
                .setSaleBeginTime(LocalDateTime.of(2026, 9, 10, 10, 0))
                .setSaleEndTime(LocalDateTime.of(2026, 12, 31, 22, 0))
                .setValidityType("DAYS_AFTER_PURCHASE")
                .setValidDays(30)
                .setUsageRulesJson(
                        "[{\"dayOfWeek\":\"MONDAY\",\"closed\":false,\"periods\":[{\"open\":\"10:00\",\"close\":\"22:00\"}]},"
                                + "{\"dayOfWeek\":\"TUESDAY\",\"closed\":false,\"periods\":[{\"open\":\"10:00\",\"close\":\"22:00\"}]},"
                                + "{\"dayOfWeek\":\"WEDNESDAY\",\"closed\":false,\"periods\":[{\"open\":\"10:00\",\"close\":\"22:00\"}]},"
                                + "{\"dayOfWeek\":\"THURSDAY\",\"closed\":false,\"periods\":[{\"open\":\"10:00\",\"close\":\"22:00\"}]},"
                                + "{\"dayOfWeek\":\"FRIDAY\",\"closed\":false,\"periods\":[{\"open\":\"10:00\",\"close\":\"22:00\"}]},"
                                + "{\"dayOfWeek\":\"SATURDAY\",\"closed\":false,\"periods\":[{\"open\":\"10:00\",\"close\":\"22:00\"}]},"
                                + "{\"dayOfWeek\":\"SUNDAY\",\"closed\":false,\"periods\":[{\"open\":\"10:00\",\"close\":\"22:00\"}]}]")
                .setExcludedDatesJson("[]")
                .setReservationRequired(false)
                .setStackable(false)
                .setRefundAnytime(true)
                .setRefundExpired(true)
                .setReviewStatus(status.name())
                .setVersion(version);
    }

    private BusinessMediaVO media(Long id) {
        return new BusinessMediaVO(
                id.toString(),
                BusinessMediaPurpose.VOUCHER_COVER,
                "券封面",
                "cover.png",
                "image/png",
                543,
                400,
                400,
                "/v1/merchant/business-media/images/" + id + "/content",
                null);
    }
}
