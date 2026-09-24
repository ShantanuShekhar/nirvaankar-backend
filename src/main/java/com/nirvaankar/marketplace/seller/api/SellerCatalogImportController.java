package com.nirvaankar.marketplace.seller.api;

import com.nirvaankar.marketplace.catalog.repository.TaxCategoryRepository;
import com.nirvaankar.marketplace.catalog.service.CategoryAttributeService;
import com.nirvaankar.marketplace.catalog.service.CategoryQueryService;
import com.nirvaankar.marketplace.common.security.AuthPrincipal;
import com.nirvaankar.marketplace.platform.service.PaymentChargeConfigService;
import com.nirvaankar.marketplace.seller.domain.CatalogImportJob;
import com.nirvaankar.marketplace.seller.service.CatalogBulkImportService;
import com.nirvaankar.marketplace.seller.service.SellerAccessGuard;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.CatalogLimitsView;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.CategoryAttributeView;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.CategoryNode;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.CategoryPathView;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.CategorySearchHit;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.ImportJobView;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.SellerPricePreviewView;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.TaxCategoryView;
import com.nirvaankar.marketplace.common.config.NirvaankarProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/seller/catalog")
@RequiredArgsConstructor
@Tag(name = "Seller / Catalog import")
public class SellerCatalogImportController {

    private final CatalogBulkImportService catalogBulkImportService;
    private final CategoryAttributeService categoryAttributeService;
    private final CategoryQueryService categoryQueryService;
    private final TaxCategoryRepository taxCategoryRepository;
    private final SellerAccessGuard sellerAccessGuard;
    private final NirvaankarProperties properties;
    private final PaymentChargeConfigService paymentChargeConfigService;
    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/categories/tree")
    @PreAuthorize("hasAuthority('product.edit')")
    @Operation(summary = "Category tree for seller catalog upload")
    public List<CategoryNode> categoryTree(@AuthenticationPrincipal AuthPrincipal principal) {
        sellerAccessGuard.requireSellerId(principal);
        return categoryQueryService.tree();
    }

    @GetMapping("/categories/children")
    @PreAuthorize("hasAuthority('product.edit')")
    public List<CategorySearchHit> categoryChildren(@AuthenticationPrincipal AuthPrincipal principal,
                                                    @RequestParam(required = false) Integer parentId) {
        sellerAccessGuard.requireSellerId(principal);
        return categoryQueryService.children(parentId);
    }

    @GetMapping("/categories/{categoryId}/path")
    @PreAuthorize("hasAuthority('product.edit')")
    public CategoryPathView categoryPath(@AuthenticationPrincipal AuthPrincipal principal,
                                         @PathVariable Integer categoryId) {
        sellerAccessGuard.requireSellerId(principal);
        return categoryAttributeService.pathFor(categoryId);
    }

    @GetMapping("/categories/{categoryId}/attributes")
    @PreAuthorize("hasAuthority('product.edit')")
    @Operation(summary = "Category-specific attributes for templates and single upload")
    public List<CategoryAttributeView> categoryAttributes(@AuthenticationPrincipal AuthPrincipal principal,
                                                          @PathVariable Integer categoryId) {
        sellerAccessGuard.requireSellerId(principal);
        return categoryAttributeService.listForCategory(categoryId);
    }

    @GetMapping("/tax-categories")
    @PreAuthorize("hasAuthority('product.edit')")
    public List<TaxCategoryView> taxCategories(@AuthenticationPrincipal AuthPrincipal principal) {
        sellerAccessGuard.requireSellerId(principal);
        return taxCategoryRepository.findAll().stream()
                .map(t -> new TaxCategoryView(t.getId(), t.getName(), t.getHsnCode(), t.getGstRate()))
                .toList();
    }

    @GetMapping("/limits")
    @PreAuthorize("hasAuthority('product.edit')")
    @Operation(summary = "Configurable catalog pricing and image limits")
    public CatalogLimitsView limits(@AuthenticationPrincipal AuthPrincipal principal) {
        sellerAccessGuard.requireSellerId(principal);
        var c = properties.catalog();
        return new CatalogLimitsView(
                c.maxSellingPriceMinor(), c.maxDiscountPercentFromMrp(),
                c.maxImagesPerProduct(), c.maxImageBytes());
    }

    @GetMapping("/price-preview")
    @PreAuthorize("hasAuthority('product.edit')")
    @Operation(summary = "Customer payable and seller receivable breakdown for a selling price")
    public SellerPricePreviewView pricePreview(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam long sellingPriceMinor,
            @RequestParam(required = false) Integer taxCategoryId) {
        Long sellerId = sellerAccessGuard.requireSellerId(principal);
        if (sellingPriceMinor < 0) {
            throw new com.nirvaankar.marketplace.common.error.ApiException(
                    com.nirvaankar.marketplace.common.error.ErrorCode.VALIDATION_FAILED,
                    "sellingPriceMinor must be >= 0");
        }
        BigDecimal gstRate = BigDecimal.ZERO;
        if (taxCategoryId != null) {
            gstRate = taxCategoryRepository.findById(taxCategoryId)
                    .map(t -> t.getGstRate())
                    .orElse(BigDecimal.ZERO);
        }
        BigDecimal commission = jdbcTemplate.query(
                "SELECT default_commission_rate FROM sellers WHERE id = ? LIMIT 1",
                rs -> rs.next() ? rs.getBigDecimal(1) : BigDecimal.TEN,
                sellerId);
        if (commission == null) {
            commission = BigDecimal.TEN;
        }
        var preview = paymentChargeConfigService.sellerPreview(sellingPriceMinor, gstRate, commission, "INR");
        return new SellerPricePreviewView(
                preview.basePriceMinor(),
                preview.productGstMinor(),
                preview.shippingMinor(),
                preview.shippingGstMinor(),
                preview.platformFeeMinor(),
                preview.platformFeeGstMinor(),
                preview.paymentGatewayFeeMinor(),
                preview.sellingInclTaxMinor(),
                preview.customerPayableMinor(),
                preview.commissionRatePercent(),
                preview.commissionMinor(),
                preview.sellerReceivableMinor(),
                preview.currency());
    }

    @GetMapping(value = "/template", produces = "text/csv")
    @PreAuthorize("hasAuthority('product.create')")
    @Operation(summary = "Download CSV template (optionally category-specific)")
    public ResponseEntity<byte[]> template(@RequestParam(required = false) Integer categoryId,
                                           @RequestParam(required = false, defaultValue = "BULK") String type) {
        byte[] body;
        String filename;
        if (CatalogImportJob.TYPE_INVENTORY_UPDATE.equalsIgnoreCase(type)) {
            body = catalogBulkImportService.buildInventoryTemplateCsv();
            filename = "nirvaankar-inventory-update-template.csv";
        } else {
            body = catalogBulkImportService.buildTemplateCsv(categoryId);
            filename = categoryId == null
                    ? "nirvaankar-catalog-template.csv"
                    : "nirvaankar-catalog-category-" + categoryId + "-template.csv";
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv"))
                .body(body);
    }

    @PostMapping(path = "/bulk-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('product.create')")
    @Operation(summary = "Bulk catalog upload (CSV/XLSX). Optional categoryId locks category-wise import.")
    public ImportJobView bulkUpload(@AuthenticationPrincipal AuthPrincipal principal,
                                    @RequestPart("file") MultipartFile file,
                                    @RequestParam(required = false, defaultValue = "BULK") String uploadType,
                                    @RequestParam(required = false) Integer categoryId) {
        Long sellerId = sellerAccessGuard.requireSellerId(principal);
        return catalogBulkImportService.upload(sellerId, principal.userId(), file, uploadType, categoryId);
    }

    @PostMapping(path = "/inventory-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('inventory.adjust')")
    @Operation(summary = "Bulk inventory/price update by SKU (no new products)")
    public ImportJobView inventoryUpload(@AuthenticationPrincipal AuthPrincipal principal,
                                         @RequestPart("file") MultipartFile file) {
        Long sellerId = sellerAccessGuard.requireSellerId(principal);
        return catalogBulkImportService.upload(
                sellerId, principal.userId(), file, CatalogImportJob.TYPE_INVENTORY_UPDATE, null);
    }

    @GetMapping("/imports")
    @PreAuthorize("hasAuthority('product.edit')")
    @Operation(summary = "List recent catalog import jobs")
    public List<ImportJobView> listImports(@AuthenticationPrincipal AuthPrincipal principal) {
        Long sellerId = sellerAccessGuard.requireSellerId(principal);
        return catalogBulkImportService.list(sellerId);
    }

    @GetMapping("/imports/{importId}")
    @PreAuthorize("hasAuthority('product.edit')")
    @Operation(summary = "Get import job status and row errors")
    public ImportJobView getImport(@AuthenticationPrincipal AuthPrincipal principal,
                                   @PathVariable UUID importId) {
        Long sellerId = sellerAccessGuard.requireSellerId(principal);
        return catalogBulkImportService.get(sellerId, importId);
    }
}
