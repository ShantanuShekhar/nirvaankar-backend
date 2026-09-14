package com.nirvaankar.marketplace.notification.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvaankar.marketplace.common.config.WhatsAppProperties;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.common.error.GlobalExceptionHandler;
import com.nirvaankar.marketplace.common.util.Hashing;
import com.nirvaankar.marketplace.common.webhook.WebhookInbox;
import com.nirvaankar.marketplace.notification.whatsapp.WhatsAppWebhookDtos.InboundMessage;
import com.nirvaankar.marketplace.notification.whatsapp.WhatsAppWebhookDtos.MessageStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WhatsAppWebhookTest {

    private static final String VERIFY_TOKEN = "my_secret_whatsapp_token_123";
    private static final String APP_SECRET = "test_whatsapp_app_secret";
    private static final String INBOUND = """
            {"object":"whatsapp_business_account","entry":[{"id":"WABA","changes":[{"field":"messages","value":{
              "messaging_product":"whatsapp",
              "metadata":{"display_phone_number":"15550001111","phone_number_id":"PNID"},
              "messages":[{"from":"919999999999","id":"wamid.INBOUND1","timestamp":"1710000000","type":"text",
                "text":{"body":"hello"}}]
            }}]}]}
            """;
    private static final String STATUS_TEMPLATE = """
            {"object":"whatsapp_business_account","entry":[{"id":"WABA","changes":[{"field":"messages","value":{
              "messaging_product":"whatsapp",
              "metadata":{"phone_number_id":"PNID"},
              "statuses":[{"id":"wamid.OUT1","status":"%s","timestamp":"1710000001","recipient_id":"919999999999"}]
            }}]}]}
            """;

    @Mock
    WebhookInbox webhookInbox;
    @Mock
    WhatsAppWebhookProcessor processor;

    WhatsAppWebhookService service;
    MockMvc mockMvc;
    RecordingHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RecordingHandler();
        WhatsAppWebhookProcessor realProcessor = new WhatsAppWebhookProcessor(List.of(handler));
        service = new WhatsAppWebhookService(
                new WhatsAppProperties(VERIFY_TOKEN, APP_SECRET),
                new ObjectMapper(),
                webhookInbox,
                processor);
        mockMvc = MockMvcBuilders.standaloneSetup(new WhatsAppWebhookController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        this.realProcessor = realProcessor;
    }

    private WhatsAppWebhookProcessor realProcessor;

    @Test
    void getVerificationWithCorrectTokenReturnsChallenge() throws Exception {
        mockMvc.perform(get("/webhook")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", VERIFY_TOKEN)
                        .param("hub.challenge", "challenge-42"))
                .andExpect(status().isOk())
                .andExpect(content().string("challenge-42"));
    }

    @Test
    void getVerificationWithWrongTokenIsForbidden() throws Exception {
        mockMvc.perform(get("/webhook")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "wrong")
                        .param("hub.challenge", "challenge-42"))
                .andExpect(status().isForbidden());
    }

    @Test
    void postValidInboundPayloadReturnsOkAndDispatchesOnce() throws Exception {
        when(webhookInbox.claim(eq("whatsapp"), anyString(), anyString(), anyString(), eq(true))).thenReturn(true);

        mockMvc.perform(post("/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", sign(INBOUND))
                        .content(INBOUND))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InboundMessage>> inbound = ArgumentCaptor.forClass(List.class);
        verify(processor).process(inbound.capture(), eq(List.of()));
        assertThat(inbound.getValue()).hasSize(1);
        assertThat(inbound.getValue().get(0).type()).isEqualTo("text");
        assertThat(inbound.getValue().get(0).messageId()).isEqualTo("wamid.INBOUND1");
    }

    @Test
    void postInvalidSignatureIsForbiddenWhenAppSecretConfigured() throws Exception {
        mockMvc.perform(post("/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", "sha256=deadbeef")
                        .content(INBOUND))
                .andExpect(status().isForbidden());
        verify(processor, never()).process(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void messageStatusPayloadsAreExtracted() {
        when(webhookInbox.claim(eq("whatsapp"), anyString(), anyString(), anyString(), eq(true))).thenReturn(true);
        WhatsAppWebhookService syncService = new WhatsAppWebhookService(
                new WhatsAppProperties(VERIFY_TOKEN, APP_SECRET),
                new ObjectMapper(),
                webhookInbox,
                realProcessor);

        for (String status : List.of("sent", "delivered", "read", "failed")) {
            handler.statuses.set(0);
            syncService.accept(STATUS_TEMPLATE.formatted(status), sign(STATUS_TEMPLATE.formatted(status)));
            assertThat(handler.lastStatus).isEqualTo(status);
            assertThat(handler.statuses.get()).isEqualTo(1);
        }
    }

    @Test
    void duplicateDeliveryDoesNotReprocess() {
        AtomicInteger claims = new AtomicInteger();
        when(webhookInbox.claim(eq("whatsapp"), anyString(), anyString(), anyString(), eq(true)))
                .thenAnswer(invocation -> claims.getAndIncrement() == 0);

        service.accept(INBOUND, sign(INBOUND));
        service.accept(INBOUND, sign(INBOUND));

        verify(processor, times(1)).process(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void malformedJsonIsAcknowledgedWithoutProcessing() throws Exception {
        mockMvc.perform(post("/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", sign("{not-json"))
                        .content("{not-json"))
                .andExpect(status().isOk());
        verify(processor, never()).process(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(webhookInbox, never()).claim(anyString(), anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void missingSignatureIsForbiddenWhenSecretConfigured() {
        assertThatThrownBy(() -> service.accept(INBOUND, null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void signatureIsSkippedWhenAppSecretNotConfigured() {
        WhatsAppWebhookService openService = new WhatsAppWebhookService(
                new WhatsAppProperties(VERIFY_TOKEN, ""),
                new ObjectMapper(),
                webhookInbox,
                processor);
        when(webhookInbox.claim(eq("whatsapp"), anyString(), anyString(), anyString(), eq(false))).thenReturn(true);

        openService.accept(INBOUND, null);

        verify(processor).process(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private static String sign(String body) {
        return "sha256=" + Hashing.hmacSha256Hex(body, APP_SECRET);
    }

    private static final class RecordingHandler implements WhatsAppWebhookEventHandler {
        private final AtomicInteger statuses = new AtomicInteger();
        private volatile String lastStatus;

        @Override
        public void onMessageStatus(MessageStatus status) {
            lastStatus = status.status();
            statuses.incrementAndGet();
        }
    }
}
