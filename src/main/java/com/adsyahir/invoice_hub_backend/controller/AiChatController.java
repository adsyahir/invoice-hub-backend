package com.adsyahir.invoice_hub_backend.controller;

import com.adsyahir.invoice_hub_backend.model.UserPrincipal;
import com.adsyahir.invoice_hub_backend.service.InvoiceAssistantTools;
import com.adsyahir.invoice_hub_backend.service.InvoiceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Streaming AI assistant, backed by Google Gemini via Spring AI.
 *
 * Streams the reply token-by-token as Server-Sent Events. Each event's data is a
 * JSON object {"c":"<chunk>"} — JSON-encoding the token keeps any newlines inside
 * it from breaking SSE's blank-line framing. The frontend's streamAssistantReply
 * generator consumes this.
 */
@RestController
@RequestMapping("/ai")
public class AiChatController {

    private static final String SYSTEM_PROMPT = """
            You are the InvoiceHub Assistant, a helpful copilot inside a Malaysian B2B
            invoicing SaaS. Help users with invoicing, receivables, payments, clients,
            and LHDN MyInvois e-invoicing. Be concise and professional, use plain
            language, and format lists clearly.

            Malaysian context: the currency is MYR, sales tax is 8% SST, and e-invoicing
            is submitted to LHDN MyInvois (document types 01-04 seller-issued, 11-14
            self-billed).

            You have tools that read this workspace's REAL invoice data (receivables
            summary, overdue invoices, top outstanding clients, MyInvois status
            breakdown). When the user asks about their actual figures, CALL the
            relevant tool and answer from its result — never invent numbers. If no
            tool fits the question, answer from general invoicing knowledge.

            You can also SEND payment-reminder emails. This is a real action, so follow
            this rule strictly: when the user asks to remind or chase a client, first
            call draftPaymentReminder to prepare the message, SHOW the draft to the
            user, and ASK them to confirm. Only call sendPaymentReminder AFTER the user
            explicitly confirms (e.g. replies "yes, send it"). Never send a reminder
            without an explicit confirmation in the conversation.
            """;

    // Used only to JSON-encode the small {"c":"<chunk>"} SSE payload. A local
    // instance avoids depending on an ObjectMapper bean (not auto-registered here).
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ChatClient chatClient;
    private final InvoiceService invoiceService;
    private final JavaMailSender mailSender;

    public AiChatController(ChatClient.Builder chatClientBuilder,
                            InvoiceService invoiceService,
                            JavaMailSender mailSender) {
        this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT).build();
        this.invoiceService = invoiceService;
        this.mailSender = mailSender;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    public Flux<ServerSentEvent<String>> chat(@RequestBody ChatRequest request,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        List<Message> messages = new ArrayList<>();
        if (request.history() != null) {
            for (Turn turn : request.history()) {
                if (turn.content() == null || turn.content().isBlank()) {
                    continue;
                }
                messages.add("assistant".equalsIgnoreCase(turn.role())
                        ? new AssistantMessage(turn.content())
                        : new UserMessage(turn.content()));
            }
        }
        messages.add(new UserMessage(request.prompt()));

        // Tools are bound to THIS user, so every data query is tenant-scoped.
        InvoiceAssistantTools tools = new InvoiceAssistantTools(principal.getUser(), invoiceService, mailSender);

        return chatClient.prompt()
                .messages(messages)
                .tools(tools)
                .stream()
                .content()
                .map(this::toEvent)
                // Explicit end-of-stream marker so the client can stop deterministically
                // instead of waiting for the connection to close (which a dev proxy may
                // not surface promptly).
                .concatWith(Flux.just(ServerSentEvent.<String>builder("{\"done\":true}").build()));
    }

    private ServerSentEvent<String> toEvent(String chunk) {
        try {
            return ServerSentEvent.builder(objectMapper.writeValueAsString(Map.of("c", chunk))).build();
        } catch (Exception e) {
            // A token that can't be serialized is dropped rather than killing the stream.
            return ServerSentEvent.builder("{\"c\":\"\"}").build();
        }
    }

    /** Request body: the new prompt plus prior turns (oldest first) for context. */
    public record ChatRequest(String prompt, List<Turn> history) {}

    public record Turn(String role, String content) {}
}
