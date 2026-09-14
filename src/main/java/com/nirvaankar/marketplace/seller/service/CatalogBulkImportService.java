package com.nirvaankar.marketplace.seller.service;

import com.nirvaankar.marketplace.catalog.domain.Category;
import com.nirvaankar.marketplace.catalog.domain.TaxCategory;
import com.nirvaankar.marketplace.catalog.repository.ProductVariantRepository;
import com.nirvaankar.marketplace.catalog.repository.TaxCategoryRepository;
import com.nirvaankar.marketplace.catalog.service.CategoryAttributeService;
import com.nirvaankar.marketplace.catalog.service.CategoryQueryService;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.common.id.UuidV7;
import com.nirvaankar.marketplace.seller.domain.CatalogImportError;
import com.nirvaankar.marketplace.seller.domain.CatalogImportJob;
import com.nirvaankar.marketplace.seller.repository.CatalogImportErrorRepository;
import com.nirvaankar.marketplace.seller.repository.CatalogImportJobRepository;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.CategoryAttributeView;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.CategoryPathView;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.CreateProductRequest;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.ImportErrorView;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.ImportJobView;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.SpecInput;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.UpdateInventoryRequest;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.UpdatePricingRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CatalogBulkImportService {

    private final CatalogImportJobRepository jobRepository;
    private final CatalogImportErrorRepository errorRepository;
    private final SellerProductService sellerProductService;
    private final CategoryAttributeService categoryAttributeService;
    private final CategoryQueryService categoryQueryService;
    private final ProductVariantRepository productVariantRepository;
    private final TaxCategoryRepository taxCategoryRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public List<ImportJobView> list(Long sellerId) {
        return jobRepository.findTop20BySellerIdOrderByCreatedAtDesc(sellerId).stream()
                .map(job -> toView(job, List.of()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ImportJobView get(Long sellerId, UUID importId) {
        CatalogImportJob job = jobRepository.findByPublicIdAndSellerId(importId, sellerId)
                .orElseThrow(() -> ApiException.notFound("Import job"));
        List<ImportErrorView> errors = errorRepository.findAllByJobIdOrderByRowNumberAscIdAsc(job.getId())
                .stream()
                .map(e -> new ImportErrorView(e.getRowNumber(), e.getSku(), e.getErrorCode(), e.getErrorMessage()))
                .toList();
        return toView(job, errors);
    }

    @Transactional
    public ImportJobView recordSingle(Long sellerId, Integer categoryId, String sku, boolean success, String error) {
        Instant now = Instant.now();
        CatalogImportJob job = jobRepository.save(CatalogImportJob.create(
                UuidV7.generate(), sellerId, CatalogImportJob.TYPE_SINGLE, categoryId,
                "single-product:" + (sku == null ? "n/a" : sku), "application/json", now));
        job.markProcessing(now);
        if (success) {
            job.complete(1, 1, 0, Instant.now());
        } else {
            errorRepository.save(CatalogImportError.of(
                    job.getId(), 1, sku, "VALIDATION",
                    error == null ? "Single product create failed" : error, Instant.now()));
            job.complete(1, 0, 1, Instant.now());
        }
        jobRepository.save(job);
        return get(sellerId, job.getPublicId());
    }

    public byte[] buildTemplateCsv(Integer categoryId) {
        StringBuilder sb = new StringBuilder();
        List<String> headers = new ArrayList<>(List.of(
                "name", "sku", "category", "subcategory", "child_category",
                "selling_price_minor", "mrp_minor", "stock", "gst_hsn", "short_desc", "status"));
        List<CategoryAttributeView> attrs = List.of();
        String cat = "";
        String sub = "";
        String child = "";
        if (categoryId != null) {
            CategoryPathView path = categoryAttributeService.pathFor(categoryId);
            cat = nullToEmpty(path.categoryName());
            sub = nullToEmpty(path.subcategoryName());
            child = nullToEmpty(path.childCategoryName());
            attrs = categoryAttributeService.listForCategory(categoryId);
            for (CategoryAttributeView attr : attrs) {
                headers.add("spec_" + attr.attributeKey());
            }
        } else {
            headers.add("spec_material");
            headers.add("spec_closure");
        }
        sb.append(String.join(",", headers)).append('\n');

        List<String> sample = new ArrayList<>();
        sample.add(quote("Sample Product"));
        sample.add(quote("SKU-DEMO-001"));
        sample.add(quote(cat.isBlank() ? "Fashion" : cat));
        sample.add(quote(sub.isBlank() ? "Bags" : sub));
        sample.add(quote(child.isBlank() ? "Handbags" : child));
        sample.add("49900");
        sample.add("59900");
        sample.add("25");
        sample.add(quote("4202"));
        sample.add(quote("Short description"));
        sample.add(quote("draft"));
        if (categoryId != null) {
            for (CategoryAttributeView attr : attrs) {
                sample.add(quote(attr.required() ? "Required value" : ""));
            }
        } else {
            sample.add(quote("Jute"));
            sample.add(quote("Zip"));
        }
        sb.append(String.join(",", sample)).append('\n');
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] buildInventoryTemplateCsv() {
        String csv = """
                sku,stock,selling_price_minor,mrp_minor
                JB001,100,49900,59900
                """;
        return csv.getBytes(StandardCharsets.UTF_8);
    }

    public ImportJobView upload(Long sellerId, Long userId, MultipartFile file,
                                String uploadType, Integer lockedCategoryId) {
        String type = normalizeUploadType(uploadType);
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Upload a CSV or XLSX file");
        }
        String filename = file.getOriginalFilename() == null ? "catalog.csv" : file.getOriginalFilename();
        String lower = filename.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".csv") && !lower.endsWith(".xlsx")) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Only .csv and .xlsx files are supported");
        }
        if (CatalogImportJob.TYPE_BULK.equals(type) && lockedCategoryId != null) {
            categoryQueryService.requireActive(lockedCategoryId);
        }

        Instant now = Instant.now();
        CatalogImportJob job = jobRepository.save(CatalogImportJob.create(
                UuidV7.generate(), sellerId, type, lockedCategoryId, filename, file.getContentType(), now));
        job.markValidating(now);
        jobRepository.save(job);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            job.fail("Could not read uploaded file", Instant.now());
            jobRepository.save(job);
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Could not read uploaded file");
        }

        List<Map<String, String>> rows;
        try {
            rows = lower.endsWith(".xlsx") ? parseXlsx(bytes) : parseCsv(bytes);
        } catch (Exception e) {
            job.fail("Invalid file format: " + e.getMessage(), Instant.now());
            jobRepository.save(job);
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid catalog file");
        }

        markDuplicateSkusInFile(rows);

        job.markProcessing(Instant.now());
        jobRepository.save(job);

        int success = 0;
        int failed = 0;
        int rowNumber = 1;
        for (Map<String, String> row : rows) {
            rowNumber++;
            String sku = trim(row.get("sku"));
            try {
                if (row.containsKey("__dup_sku")) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED,
                            "Duplicate SKU within file: " + sku);
                }
                if (CatalogImportJob.TYPE_INVENTORY_UPDATE.equals(type)) {
                    applyInventoryUpdate(sellerId, userId, row);
                } else {
                    CreateProductRequest request = toCreateRequest(sellerId, row, lockedCategoryId);
                    sellerProductService.create(sellerId, userId, request);
                }
                success++;
            } catch (ApiException ex) {
                failed++;
                errorRepository.save(CatalogImportError.of(
                        job.getId(), rowNumber, sku,
                        ex.getErrorCode() == null ? "VALIDATION" : ex.getErrorCode().name(),
                        formatRowError(ex.getMessage()),
                        Instant.now()));
            } catch (Exception ex) {
                failed++;
                errorRepository.save(CatalogImportError.of(
                        job.getId(), rowNumber, sku, "UNEXPECTED",
                        formatRowError(ex.getMessage() == null ? "Unexpected error" : ex.getMessage()),
                        Instant.now()));
            }
        }

        job.complete(rows.size(), success, failed, Instant.now());
        jobRepository.save(job);
        return get(sellerId, job.getPublicId());
    }

    private void applyInventoryUpdate(Long sellerId, Long userId, Map<String, String> row) {
        String sku = required(row, "sku").toUpperCase(Locale.ROOT);
        Integer stock = parseIntOptional(row, "stock");
        if (stock == null) {
            stock = parseIntOptional(row, "new_stock");
        }
        if (stock == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "stock is required for inventory update");
        }
        if (stock < 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "stock cannot be negative");
        }

        OwnedVariant owned = findOwnedVariantBySku(sellerId, sku)
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                        "SKU not found for this seller: " + sku));

        sellerProductService.updateInventory(sellerId, userId, owned.productPublicId(),
                new UpdateInventoryRequest(stock));

        Long selling = parseLongOptional(row, "selling_price_minor");
        Long mrp = parseLongOptional(row, "mrp_minor");
        if (selling != null) {
            sellerProductService.updatePricing(sellerId, userId, owned.productPublicId(),
                    new UpdatePricingRequest(selling, mrp));
        }
    }

    private CreateProductRequest toCreateRequest(Long sellerId, Map<String, String> row, Integer lockedCategoryId) {
        String name = required(row, "name");
        String sku = required(row, "sku");

        Integer categoryId = lockedCategoryId;
        if (categoryId == null) {
            Integer explicitId = parseIntOptional(row, "category_id");
            if (explicitId != null) {
                categoryId = categoryQueryService.requireActive(explicitId).getId();
            } else {
                String category = firstNonBlank(row, "category", "category_name");
                String subcategory = firstNonBlank(row, "subcategory", "sub_category", "subcategory_name");
                String child = firstNonBlank(row, "child_category", "childcategory", "child_category_name");
                Category resolved = categoryAttributeService
                        .resolveHierarchy(category, subcategory, child)
                        .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                                "category_id or category/subcategory/child_category is required"));
                categoryId = categoryQueryService.requireActive(resolved.getId()).getId();
            }
        } else {
            categoryQueryService.requireActive(categoryId);
            String category = firstNonBlank(row, "category", "category_name");
            String subcategory = firstNonBlank(row, "subcategory", "sub_category");
            String child = firstNonBlank(row, "child_category", "childcategory");
            if (category != null || subcategory != null || child != null) {
                Category resolved = categoryAttributeService
                        .resolveHierarchy(category, subcategory, child)
                        .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                                "Invalid category hierarchy for locked category"));
                if (!resolved.getId().equals(categoryId)) {
                    CategoryPathView expected = categoryAttributeService.pathFor(categoryId);
                    throw new ApiException(ErrorCode.VALIDATION_FAILED,
                            "Invalid category hierarchy. Expected: " + expected.breadcrumb());
                }
            }
        }

        String skuNorm = sku.trim().toUpperCase(Locale.ROOT);
        if (skuExistsForSeller(sellerId, skuNorm) || productVariantRepository.existsBySku(skuNorm)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "SKU already exists for seller: " + skuNorm);
        }

        Long selling = parseLongRequired(row, "selling_price_minor");
        Long mrp = parseLongOptional(row, "mrp_minor");
        if (mrp == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "mrp_minor is required");
        }
        if (mrp <= selling) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "MRP must be greater than selling price");
        }
        Integer stock = parseIntOptional(row, "stock");
        if (stock != null && stock < 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "stock cannot be negative");
        }
        String status = trim(row.get("status"));
        String shortDesc = trim(row.get("short_desc"));
        Integer taxCategoryId = resolveTaxCategoryId(row);

        List<SpecInput> specs = extractSpecs(row);
        categoryAttributeService.validateRequiredSpecs(categoryId, specs);

        return new CreateProductRequest(
                name, skuNorm, categoryId, taxCategoryId, shortDesc, null, null, null, null,
                true, null, null, null, specs, selling, mrp, stock == null ? 0 : stock, status);
    }

    private boolean skuExistsForSeller(Long sellerId, String sku) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM product_variants pv
                  JOIN products p ON p.id = pv.product_id
                 WHERE UPPER(pv.sku) = ? AND p.seller_id = ? AND pv.deleted_at IS NULL AND p.deleted_at IS NULL
                """, Integer.class, sku.trim().toUpperCase(Locale.ROOT), sellerId);
        return count != null && count > 0;
    }

    private Integer resolveTaxCategoryId(Map<String, String> row) {
        String hsn = firstNonBlank(row, "gst_hsn", "hsn", "hsn_code");
        if (hsn == null) {
            return null;
        }
        return taxCategoryRepository.findAll().stream()
                .filter(t -> hsn.equalsIgnoreCase(t.getHsnCode()))
                .map(TaxCategory::getId)
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Unknown GST/HSN code: " + hsn));
    }

    private List<SpecInput> extractSpecs(Map<String, String> row) {
        List<SpecInput> specs = new ArrayList<>();
        int order = 0;
        for (Map.Entry<String, String> entry : row.entrySet()) {
            String key = entry.getKey();
            if (key == null || entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            String lower = key.toLowerCase(Locale.ROOT);
            if (lower.startsWith("spec_")) {
                String attr = key.substring("spec_".length()).replace('_', ' ').trim();
                specs.add(new SpecInput(attr, entry.getValue().trim(), order++));
            }
        }
        return specs;
    }

    private Optional<OwnedVariant> findOwnedVariantBySku(Long sellerId, String sku) {
        return jdbcTemplate.query("""
                SELECT pv.id AS variant_id, p.public_id AS product_public_id
                  FROM product_variants pv
                  JOIN products p ON p.id = pv.product_id
                 WHERE UPPER(pv.sku) = ? AND p.seller_id = ?
                   AND pv.deleted_at IS NULL AND p.deleted_at IS NULL
                 LIMIT 1
                """, rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            byte[] pub = rs.getBytes("product_public_id");
            UUID publicId = uuidFromBytes(pub);
            return Optional.of(new OwnedVariant(rs.getLong("variant_id"), publicId.toString()));
        }, sku, sellerId);
    }

    private void markDuplicateSkusInFile(List<Map<String, String>> rows) {
        Set<String> seen = new HashSet<>();
        Set<String> dups = new HashSet<>();
        for (Map<String, String> row : rows) {
            String sku = trim(row.get("sku"));
            if (sku == null) {
                continue;
            }
            String key = sku.toUpperCase(Locale.ROOT);
            if (!seen.add(key)) {
                dups.add(key);
            }
        }
        for (Map<String, String> row : rows) {
            String sku = trim(row.get("sku"));
            if (sku != null && dups.contains(sku.toUpperCase(Locale.ROOT))) {
                row.put("__dup_sku", "1");
            }
        }
    }

    private static String normalizeUploadType(String uploadType) {
        if (uploadType == null || uploadType.isBlank()) {
            return CatalogImportJob.TYPE_BULK;
        }
        String t = uploadType.trim().toUpperCase(Locale.ROOT);
        if (CatalogImportJob.TYPE_BULK.equals(t)
                || CatalogImportJob.TYPE_INVENTORY_UPDATE.equals(t)
                || CatalogImportJob.TYPE_SINGLE.equals(t)) {
            return t;
        }
        throw new ApiException(ErrorCode.VALIDATION_FAILED,
                "uploadType must be BULK, INVENTORY_UPDATE, or SINGLE");
    }

    private static String formatRowError(String message) {
        if (message == null || message.isBlank()) {
            return "Row failed";
        }
        return message;
    }

    private List<Map<String, String>> parseCsv(byte[] bytes) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {
            List<Map<String, String>> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                Map<String, String> map = new LinkedHashMap<>();
                for (String header : parser.getHeaderNames()) {
                    map.put(normalizeHeader(header), record.isMapped(header) ? record.get(header) : null);
                }
                if (map.values().stream().anyMatch(v -> v != null && !v.isBlank())) {
                    rows.add(map);
                }
            }
            return rows;
        }
    }

    private List<Map<String, String>> parseXlsx(byte[] bytes) throws IOException {
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                return List.of();
            }
            Iterator<Row> it = sheet.iterator();
            if (!it.hasNext()) {
                return List.of();
            }
            Row headerRow = it.next();
            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(normalizeHeader(formatter.formatCellValue(cell)));
            }
            List<Map<String, String>> rows = new ArrayList<>();
            while (it.hasNext()) {
                Row row = it.next();
                Map<String, String> map = new LinkedHashMap<>();
                boolean any = false;
                for (int i = 0; i < headers.size(); i++) {
                    Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String value = cell == null ? null : formatter.formatCellValue(cell).trim();
                    if (value != null && !value.isBlank()) {
                        any = true;
                    }
                    map.put(headers.get(i), value);
                }
                if (any) {
                    rows.add(map);
                }
            }
            return rows;
        }
    }

    private static String normalizeHeader(String header) {
        return header == null ? "" : header.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static String required(Map<String, String> row, String key) {
        String value = trim(row.get(key));
        if (value == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, key + " is required");
        }
        return value;
    }

    private static String firstNonBlank(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String value = trim(row.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String quote(String value) {
        String v = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + v + "\"";
    }

    private static Integer parseIntOptional(Map<String, String> row, String key) {
        String value = trim(row.get(key));
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.replace(",", "").replace(".0", ""));
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, key + " must be an integer");
        }
    }

    private static Long parseLongRequired(Map<String, String> row, String key) {
        String value = required(row, key);
        try {
            return Long.parseLong(value.replace(",", "").replace(".0", ""));
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, key + " must be a number (paise)");
        }
    }

    private static Long parseLongOptional(Map<String, String> row, String key) {
        String value = trim(row.get(key));
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.replace(",", "").replace(".0", ""));
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, key + " must be a number (paise)");
        }
    }

    private ImportJobView toView(CatalogImportJob job, List<ImportErrorView> errors) {
        String categoryPath = null;
        if (job.getCategoryId() != null) {
            try {
                categoryPath = categoryAttributeService.pathFor(job.getCategoryId()).breadcrumb();
            } catch (Exception ignored) {
                categoryPath = null;
            }
        }
        return new ImportJobView(
                job.getPublicId(),
                job.getUploadType() == null ? CatalogImportJob.TYPE_BULK : job.getUploadType(),
                job.getCategoryId(),
                categoryPath,
                job.getOriginalFilename(), job.getStatus(),
                job.getTotalRows(), job.getSuccessfulRows(), job.getFailedRows(),
                job.getErrorSummary(), job.getStartedAt(), job.getCompletedAt(),
                job.getCreatedAt(), errors);
    }

    private static UUID uuidFromBytes(byte[] bytes) {
        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (bytes[i] & 0xff);
        }
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (bytes[i] & 0xff);
        }
        return new UUID(msb, lsb);
    }

    private record OwnedVariant(long variantId, String productPublicId) {
    }
}
