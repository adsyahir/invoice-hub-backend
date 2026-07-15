package com.adsyahir.invoice_hub_backend.consumer;

import com.adsyahir.invoice_hub_backend.config.KafkaTopicConfig;
import com.adsyahir.invoice_hub_backend.event.PaymentRecordedEvent;
import com.adsyahir.invoice_hub_backend.service.InvoiceEmailService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.stereotype.Component;

/**
 * Emails the client a receipt when a payment is recorded against their invoice.
 *
 * <p>Separate class from {@link InvoiceEmailConsumer} because Spring Kafka permits only one
 * {@code @DltHandler} per class — each listener needs its own dead-letter handler.
 */
@Component
@RequiredArgsConstructor
public class PaymentReceiptConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentReceiptConsumer.class);

    private final InvoiceEmailService emailService;

    @RetryableTopic(
            attempts = "4",
            backOff = @BackOff(delay = 2000, multiplier = 2.0),
            dltTopicSuffix = "-dlt")
    @KafkaListener(topics = KafkaTopicConfig.PAYMENT_EVENTS, groupId = "payment-receipt")
    public void onPaymentRecorded(PaymentRecordedEvent event) {
        log.debug("Emailing receipt for payment {} (eventId={})", event.paymentUuid(), event.eventId());

        emailService.sendReceipt(event.invoiceId(), event.amount(), event.settlesInvoice());
    }

    @DltHandler
    public void dlt(PaymentRecordedEvent event) {
        log.error("Payment receipt PERMANENTLY failed after retries — payment={} eventId={}",
                event.paymentUuid(), event.eventId());
    }
}
