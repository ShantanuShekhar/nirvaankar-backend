package com.nirvaankar.marketplace.seller.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily seller settlement. ShedLock prevents two instances settling the same batch.
 * After ledger payout rows are created, pending amounts are pushed via Razorpay Route.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SellerSettlementJob {

    private final SellerSettlementService settlementService;
    private final RazorpayRouteSettlementService routeSettlementService;

    @Scheduled(cron = "0 30 6 * * *", zone = "Asia/Kolkata")
    @SchedulerLock(name = "sellerDailySettlement", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void runDaily() {
        var today = SellerSettlementService.todayIst();
        log.info("Seller settlement job started date={}", today);
        int created = settlementService.settleEligibleSellers(today);
        int transferred = 0;
        try {
            transferred = routeSettlementService.processPendingTransfers();
        } catch (Exception e) {
            log.error("Razorpay Route settlement failed date={}: {}", today, e.getMessage());
        }
        log.info("Seller settlement job finished date={} payoutsCreated={} routeTransfers={}",
                today, created, transferred);
    }
}