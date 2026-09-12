package com.ray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ray.constant.AdminPermissions;
import com.ray.entity.FundLedgerEntry;
import com.ray.entity.SettlementAttempt;
import com.ray.entity.SettlementBatch;
import com.ray.entity.SettlementItem;
import com.ray.exception.BusinessException;
import com.ray.mapper.FundLedgerEntryMapper;
import com.ray.mapper.SettlementAttemptMapper;
import com.ray.mapper.SettlementBatchMapper;
import com.ray.mapper.SettlementItemMapper;
import com.ray.realtime.RealtimeEventPublisher;
import com.ray.result.PageResult;
import com.ray.service.AdminAuthService;
import com.ray.service.MerchantAuthService;
import com.ray.service.SettlementService;
import com.ray.utils.converter.IdUtils;
import com.ray.utils.generator.RedisIdWorker;
import com.ray.vo.SettlementBatchVO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 结算批次查询、Mock 执行尝试、重试和 T+1 批次生成。 */
@Slf4j
@Service
public class SettlementServiceImpl implements SettlementService {
    private final SettlementBatchMapper batchMapper;
    private final SettlementItemMapper itemMapper;
    private final SettlementAttemptMapper attemptMapper;
    private final FundLedgerEntryMapper ledgerMapper;
    private final AdminAuthService admin;
    private final MerchantAuthService merchant;
    private final RedisIdWorker ids;
    private RealtimeEventPublisher realtimeEvents;

    public SettlementServiceImpl(SettlementBatchMapper batchMapper, SettlementItemMapper itemMapper,
            SettlementAttemptMapper attemptMapper, FundLedgerEntryMapper ledgerMapper,
            AdminAuthService admin, MerchantAuthService merchant, RedisIdWorker ids) {
        this.batchMapper = batchMapper;
        this.itemMapper = itemMapper;
        this.attemptMapper = attemptMapper;
        this.ledgerMapper = ledgerMapper;
        this.admin = admin;
        this.merchant = merchant;
        this.ids = ids;
    }

    @Autowired(required = false)
    void setRealtimeEvents(RealtimeEventPublisher realtimeEvents) {
        this.realtimeEvents = realtimeEvents;
    }

    /** 分页查询结算批次，并按管理端或当前商户权限隔离门店。 */
    @Override
    public PageResult<SettlementBatchVO> list(int page, int size, boolean isAdmin) {
        Long shopId = null;
        if (isAdmin) {
            admin.requirePermission(AdminPermissions.SETTLEMENT_MANAGE);
        } else {
            var account = merchant.requireCurrentAccount();
            shopId = account.getShopId();
            if (shopId == null) {
                throw BusinessException.forbidden("MERCHANT_ACTIVATION_REQUIRED", "商户账号尚未激活");
            }
        }
        QueryWrapper<SettlementBatch> query = new QueryWrapper<>();
        if (shopId != null) {
            query.eq("shop_id", shopId);
        }
        Page<SettlementBatch> result = batchMapper.selectPage(new Page<>(page, size),
                query.orderByDesc("settlement_date", "id"));
        return new PageResult<>(result.getRecords().stream().map(this::toVO).toList(), page, size, result.getTotal());
    }

    /** 查询单个结算批次并执行门店隔离。 */
    @Override
    public SettlementBatchVO get(Long id, boolean isAdmin) {
        SettlementBatch batch = batchMapper.selectById(id);
        if (batch == null) {
            throw BusinessException.notFound("SETTLEMENT_NOT_FOUND", "结算批次不存在");
        }
        if (isAdmin) {
            admin.requirePermission(AdminPermissions.SETTLEMENT_MANAGE);
        } else {
            var account = merchant.requireCurrentAccount();
            if (!Objects.equals(account.getShopId(), batch.getShopId())) {
                throw BusinessException.notFound("SETTLEMENT_NOT_FOUND", "结算批次不存在");
            }
        }
        return toVO(batch);
    }

    /** 创建一次新的 Mock 结算执行尝试，不能通过改状态伪造重试成功。 */
    @Override
    @Transactional
    public SettlementBatchVO retry(Long id, String key) {
        admin.requirePermission(AdminPermissions.SETTLEMENT_MANAGE);
        if (key == null || key.isBlank()) {
            throw BusinessException.badRequest("IDEMPOTENCY_KEY_REQUIRED", "结算重试必须提供幂等键");
        }
        SettlementBatch batch = batchMapper.selectById(id);
        if (batch == null) {
            throw BusinessException.notFound("SETTLEMENT_NOT_FOUND", "结算批次不存在");
        }
        if ("SUCCEEDED".equals(batch.getStatus())) {
            throw BusinessException.conflict("SETTLEMENT_ALREADY_SUCCEEDED", "结算已完成");
        }
        SettlementAttempt existing = attemptMapper.findByIdempotencyKey(key);
        if (existing != null) {
            return toVO(batchMapper.selectById(id));
        }
        SettlementAttempt attempt = new SettlementAttempt()
                .setId(ids.nextId("settlement-attempt"))
                .setBatchId(batch.getId())
                .setIdempotencyKey(key)
                .setStatus("PROCESSING")
                .setRequestAmount(batch.getTotalAmount())
                .setMockScenario("SUCCESS")
                .setRetryCount(1)
                .setStartedTime(LocalDateTime.now());
        try {
            attemptMapper.insert(attempt);
        } catch (DuplicateKeyException duplicate) {
            return toVO(batchMapper.selectById(id));
        }
        executeMockSettlement(batch, attempt);
        return toVO(batchMapper.selectById(id));
    }

    /** 为指定结算日生成前一日可结算账本，并创建成功的默认 Mock 执行尝试。 */
    @Override
    @Transactional
    public void generateForDate(LocalDate settlementDate) {
        if (settlementDate == null) {
            throw BusinessException.badRequest("SETTLEMENT_DATE_REQUIRED", "结算日期不能为空");
        }
        LocalDate ledgerDate = settlementDate.minusDays(1);
        LocalDateTime from = ledgerDate.atStartOfDay();
        LocalDateTime to = settlementDate.atStartOfDay();
        List<FundLedgerEntry> entries = ledgerMapper.selectList(new QueryWrapper<FundLedgerEntry>()
                .ge("occurred_time", from).lt("occurred_time", to).isNotNull("shop_id")
                .in("entry_type", "REDEMPTION_RECOGNIZED", "SERVICE_FEE_RECOGNIZED", "COMMISSION_RECOGNIZED",
                        "REDEMPTION_REVERSED", "SERVICE_FEE_REVERSED", "COMMISSION_REVERSED",
                        "REFUND_REVENUE_REVERSED", "SERVICE_FEE_REFUNDED", "SETTLEMENT_ADJUSTMENT")
                .orderByAsc("shop_id", "occurred_time", "id"));
        Map<Long, List<FundLedgerEntry>> byShop = entries.stream().collect(
                Collectors.groupingBy(FundLedgerEntry::getShopId, LinkedHashMap::new, Collectors.toList()));
        byShop.forEach((shopId, shopEntries) -> createAndExecuteBatch(shopId, settlementDate, shopEntries));
    }

    /** 创建批次、明细和首次执行尝试；已有批次保持幂等。 */
    private void createAndExecuteBatch(Long shopId, LocalDate settlementDate, List<FundLedgerEntry> entries) {
        if (batchMapper.findByShopDate(shopId, settlementDate) != null) {
            return;
        }
        long total = entries.stream().mapToLong(this::settlementAmount).sum();
        SettlementBatch batch = new SettlementBatch().setId(ids.nextId("settlement-batch"))
                .setShopId(shopId).setSettlementDate(settlementDate).setStatus("PROCESSING")
                .setTotalAmount(total).setVersion(0);
        batchMapper.insert(batch);
        for (FundLedgerEntry entry : entries) {
            itemMapper.insert(new SettlementItem().setId(ids.nextId("settlement-item"))
                    .setBatchId(batch.getId()).setLedgerEntryId(entry.getId()).setAmount(settlementAmount(entry)));
        }
        SettlementAttempt attempt = new SettlementAttempt().setId(ids.nextId("settlement-attempt"))
                .setBatchId(batch.getId()).setIdempotencyKey("SETTLEMENT-" + batch.getId())
                .setStatus("PROCESSING").setRequestAmount(total).setMockScenario("SUCCESS")
                .setRetryCount(0).setStartedTime(LocalDateTime.now());
        attemptMapper.insert(attempt);
        executeMockSettlement(batch, attempt);
    }

    /** Mock 渠道执行成功后同时落执行尝试和批次最终状态。 */
    private void executeMockSettlement(SettlementBatch batch, SettlementAttempt attempt) {
        LocalDateTime now = LocalDateTime.now();
        attempt.setStatus("SUCCESS").setProviderReference("MOCK-SETTLEMENT-" + attempt.getId())
                .setFinishedTime(now);
        attemptMapper.updateById(attempt);
        batch.setStatus("SUCCEEDED").setFailureReason(null).setProcessedTime(now);
        batchMapper.updateById(batch);
        log.info("[结算执行] Mock 结算成功，batchId={}，attemptId={}，amount={}",
                batch.getId(), attempt.getId(), batch.getTotalAmount());
        if (realtimeEvents != null) {
            realtimeEvents.publish("SETTLEMENT_UPDATED", batch.getId().toString(), batch.getShopId());
        }
    }

    /** 将不可变账本分录转换为商户应结算金额。 */
    private long settlementAmount(FundLedgerEntry entry) {
        long amount = entry.getAmount() == null ? 0 : entry.getAmount();
        if ("SERVICE_FEE_RECOGNIZED".equals(entry.getEntryType())
                || "COMMISSION_RECOGNIZED".equals(entry.getEntryType())) {
            return -Math.abs(amount);
        }
        if ("SERVICE_FEE_REVERSED".equals(entry.getEntryType())
                || "COMMISSION_REVERSED".equals(entry.getEntryType())
                || "SERVICE_FEE_REFUNDED".equals(entry.getEntryType())) {
            return Math.abs(amount);
        }
        return amount > 0 && "DEBIT".equals(entry.getAccountSide()) ? -amount : amount;
    }

    /** 导出账本或结算批次。 */
    @Override
    public byte[] export(String resource, boolean isAdmin) {
        if (isAdmin) {
            admin.requirePermission(AdminPermissions.SETTLEMENT_MANAGE);
        } else {
            merchant.requireCurrentAccount();
        }
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"ID", "门店ID", "金额(分)", "状态", "时间"});
        if ("ledger".equals(resource)) {
            for (FundLedgerEntry entry : ledgerMapper.selectList(
                    new QueryWrapper<FundLedgerEntry>().orderByDesc("occurred_time").last("LIMIT 10000"))) {
                rows.add(new String[]{IdUtils.format(entry.getId()), IdUtils.format(entry.getShopId()),
                        String.valueOf(entry.getAmount()), entry.getEntryType(), String.valueOf(entry.getOccurredTime())});
            }
        } else {
            for (SettlementBatch batch : batchMapper.selectList(
                    new QueryWrapper<SettlementBatch>().orderByDesc("settlement_date").last("LIMIT 10000"))) {
                rows.add(new String[]{IdUtils.format(batch.getId()), IdUtils.format(batch.getShopId()),
                        String.valueOf(batch.getTotalAmount()), batch.getStatus(), String.valueOf(batch.getSettlementDate())});
            }
        }
        return xlsx(rows);
    }

    private SettlementBatchVO toVO(SettlementBatch batch) {
        return new SettlementBatchVO(IdUtils.format(batch.getId()), IdUtils.format(batch.getShopId()),
                batch.getSettlementDate(), batch.getStatus(), batch.getTotalAmount(), batch.getFailureReason(),
                batch.getProcessedTime());
    }

    private byte[] xlsx(List<String[]> rows) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                put(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
                put(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
                put(zip, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Roamly\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
                put(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
                StringBuilder sheet = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
                int row = 1;
                for (String[] values : rows) {
                    sheet.append("<row r=\"").append(row++).append("\">");
                    for (int i = 0; i < values.length; i++) {
                        String value = values[i] == null ? "" : values[i].replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                        if (value.startsWith("=") || value.startsWith("+") || value.startsWith("-") || value.startsWith("@")) {
                            value = "'" + value;
                        }
                        sheet.append("<c r=\"").append((char) ('A' + i)).append(row - 1)
                                .append("\" t=\"inlineStr\"><is><t>").append(value).append("</t></is></c>");
                    }
                    sheet.append("</row>");
                }
                sheet.append("</sheetData></worksheet>");
                put(zip, "xl/worksheets/sheet1.xml", sheet.toString());
            }
            return out.toByteArray();
        } catch (IOException exception) {
            throw new BusinessException(500, "EXPORT_FAILED", "导出文件生成失败", exception);
        }
    }

    private void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
