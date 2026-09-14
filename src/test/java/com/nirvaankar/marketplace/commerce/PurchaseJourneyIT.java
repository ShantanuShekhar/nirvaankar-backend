package com.nirvaankar.marketplace.commerce;

import com.nirvaankar.marketplace.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PurchaseJourneyIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void publishedProductsAreListedAndDraftIsHidden() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].slug", hasItem("terracotta-diya-set")))
                .andExpect(jsonPath("$.items[*].slug", not(hasItem("unpublished-sample"))));

        mockMvc.perform(get("/api/v1/catalog/products/unpublished-sample"))
                .andExpect(status().isNotFound());
    }

    @Test
    void registerAddToCartCheckoutAndPay() throws Exception {
        String email = "buyer-" + UUID.randomUUID() + "@nirvaankar.test";
        MvcResult register = mockMvc.perform(post("/api/v1/auth/register")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password12","firstName":"Buyer"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(register.getResponse().getContentAsString(), "$.tokens.accessToken");

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"NRV-DIYA-SET-4\",\"quantity\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.grandTotalMinor").isNumber());

        MvcResult address = mockMvc.perform(post("/api/v1/me/addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contactName":"Buyer","contactPhone":"+919811122233","line1":"12 Clay Lane",
                                 "city":"Mumbai","state":"Maharashtra","pincode":"400001","makeDefault":true}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Number addressId = com.jayway.jsonpath.JsonPath.read(address.getResponse().getContentAsString(), "$.id");

        MvcResult order = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":%s}".formatted(addressId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderStatus").value("pending"))
                .andExpect(jsonPath("$.paymentStatus").value("pending"))
                .andReturn();
        String orderId = com.jayway.jsonpath.JsonPath.read(order.getResponse().getContentAsString(), "$.orderId");

        MvcResult pay = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"%s\"}".formatted(orderId)))
                .andExpect(status().isCreated())
                .andReturn();
        String paymentId = com.jayway.jsonpath.JsonPath.read(pay.getResponse().getContentAsString(), "$.paymentId");

        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/simulate-capture")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("paid"))
                .andExpect(jsonPath("$.orderStatus").value("confirmed"))
                .andExpect(jsonPath("$.orderNumber", containsString("NRV-")));
    }
}
