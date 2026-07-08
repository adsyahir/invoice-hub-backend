package com.adsyahir.invoice_hub_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling   // enables the daily overdue-invoice sweep (OverdueInvoiceJob)
public class InvoiceHubBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(InvoiceHubBackendApplication.class, args);
	}

}	
