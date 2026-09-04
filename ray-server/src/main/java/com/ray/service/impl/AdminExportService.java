package com.ray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ray.constant.AdminPermissions;
import com.ray.entity.*;
import com.ray.exception.BusinessException;
import com.ray.mapper.*;
import com.ray.service.AdminAuthService;
import com.ray.utils.converter.IdUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;

/** 管理端同步 XLSX 导出，复用查询事实并限制单次 10000 行。 */
@Service
public class AdminExportService {
    private final AdminAuthService admin;
    private final MerchantApplicationMapper applications;
    private final VoucherProductMapper products;
    private final VoucherOrderMapper orders;
    private final VoucherRefundMapper refunds;
    private final VoucherRedemptionMapper redemptions;
    private final FundLedgerEntryMapper ledger;
    private final SettlementBatchMapper settlements;
    private final OperationAuditLogMapper audits;

    public AdminExportService(AdminAuthService admin, MerchantApplicationMapper applications,
            VoucherProductMapper products, VoucherOrderMapper orders, VoucherRefundMapper refunds,
            VoucherRedemptionMapper redemptions, FundLedgerEntryMapper ledger,
            SettlementBatchMapper settlements, OperationAuditLogMapper audits) {
        this.admin = admin; this.applications = applications; this.products = products;
        this.orders = orders; this.refunds = refunds; this.redemptions = redemptions;
        this.ledger = ledger; this.settlements = settlements; this.audits = audits;
    }

    public byte[] export(String resource) {
        String normalized = resource == null ? "" : resource.trim().toLowerCase(Locale.ROOT);
        String[] fields = switch (normalized) {
            case "merchant-applications", "merchant_applications", "applications" -> { admin.requirePermission(AdminPermissions.MERCHANT_APPLICATION_REVIEW); yield new String[]{"id","merchantAccountId","status","shopName","cityCode","submittedAt","reviewedAt"}; }
            case "vouchers", "voucher-products", "voucher_products" -> { admin.requirePermission(AdminPermissions.VOUCHER_REVIEW); yield new String[]{"id","shopId","productType","title","priceAmount","totalStock","availableStock","soldCount","reviewStatus","saleStatus"}; }
            case "orders" -> { admin.requirePermission(AdminPermissions.TRADE_READ); yield new String[]{"id","userId","shopId","productId","productTitle","quantity","payAmount","status","createTime","payTime"}; }
            case "refunds" -> { admin.requirePermission(AdminPermissions.REFUND_MANAGE); yield new String[]{"id","voucherId","orderId","userId","amount","status","reason","requestedTime","processedTime"}; }
            case "redemptions" -> { admin.requirePermission(AdminPermissions.TRADE_READ); yield new String[]{"id","voucherId","shopId","merchantAccountId","useCount","discountAmount","status","redeemedTime","reversedTime"}; }
            case "ledger", "ledger-entries", "ledger_entries" -> { admin.requirePermission(AdminPermissions.COMMISSION_MANAGE); yield new String[]{"id","shopId","orderId","entryType","accountSide","amount","occurredTime"}; }
            case "settlements", "settlement" -> { admin.requirePermission(AdminPermissions.SETTLEMENT_MANAGE); yield new String[]{"id","shopId","settlementDate","totalAmount","status","failureReason","processedTime"}; }
            case "audits", "audit-logs", "audit_logs" -> { admin.requirePermission(AdminPermissions.AUDIT_READ); yield new String[]{"id","actorType","actorId","action","objectType","objectId","result","createTime"}; }
            default -> throw BusinessException.badRequest("EXPORT_RESOURCE_INVALID", "不支持的导出资源");
        };
        List<?> records = switch (normalized) {
            case "merchant-applications", "merchant_applications", "applications" -> rows(applications);
            case "vouchers", "voucher-products", "voucher_products" -> rows(products);
            case "orders" -> rows(orders);
            case "refunds" -> rows(refunds);
            case "redemptions" -> rows(redemptions);
            case "ledger", "ledger-entries", "ledger_entries" -> rows(ledger);
            case "settlements", "settlement" -> rows(settlements);
            case "audits", "audit-logs", "audit_logs" -> rows(audits);
            default -> List.of();
        };
        List<String[]> output = new ArrayList<>();
        output.add(Arrays.stream(fields).map(this::label).toArray(String[]::new));
        records.stream().limit(10_000).forEach(record -> output.add(Arrays.stream(fields).map(field -> format(read(record, field))).toArray(String[]::new)));
        return xlsx(output);
    }

    private <T> List<T> rows(BaseMapper<T> mapper) { return mapper.selectList(new QueryWrapper<T>().last("LIMIT 10000")); }
    private Object read(Object value, String name) {
        if (value == null) return null;
        try { Field field = value.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(value); }
        catch (ReflectiveOperationException ignored) { return null; }
    }
    private String format(Object value) {
        if (value == null) return "";
        if (value instanceof Long id && id > 0) return IdUtils.format(id);
        return String.valueOf(value);
    }
    private String label(String field) { return switch (field) { case "id" -> "ID"; case "shopId" -> "门店 ID"; case "status" -> "状态"; case "createTime" -> "创建时间"; default -> field; }; }
    private byte[] xlsx(List<String[]> rows) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(out)) {
            put(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
            put(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            put(zip, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Roamly\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            put(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
            StringBuilder sheet = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
            int row = 1; for (String[] values : rows) { sheet.append("<row r=\"").append(row++).append("\">"); for (int i=0;i<values.length;i++) { String value = values[i] == null ? "" : values[i].replace("&","&amp;").replace("<","&lt;").replace(">","&gt;"); if (value.startsWith("=") || value.startsWith("+") || value.startsWith("-") || value.startsWith("@")) value = "'" + value; sheet.append("<c r=\"").append((char)('A'+i)).append(row-1).append("\" t=\"inlineStr\"><is><t>").append(value).append("</t></is></c>"); } sheet.append("</row>"); }
            sheet.append("</sheetData></worksheet>"); put(zip, "xl/worksheets/sheet1.xml", sheet.toString()); return out.toByteArray();
        } catch (IOException e) { throw new BusinessException(500, "EXPORT_FAILED", "导出文件生成失败", e); }
    }
    private void put(ZipOutputStream zip, String name, String value) throws IOException { zip.putNextEntry(new ZipEntry(name)); zip.write(value.getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); }
}
