package com.nirvaankar.marketplace.seller.service;

import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.common.security.AuthPrincipal;
import org.springframework.stereotype.Component;

@Component
public class SellerAccessGuard {

    public Long requireSellerId(AuthPrincipal principal) {
        if (principal == null) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Authentication required");
        }
        Long sellerId = principal.sellerId();
        if (sellerId == null) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Seller account required");
        }
        return sellerId;
    }
}
