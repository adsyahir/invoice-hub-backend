package com.adsyahir.invoice_hub_backend.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/** The search projection of a client. See InvoiceDocument for the design notes. */
@Document(indexName = "clients")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientDocument {

    @Id
    private Long id;

    @Field(type = FieldType.Long)
    private Long tenantId;

    /** Public handle the frontend navigates with (/clients/{uuid}). */
    @Field(type = FieldType.Keyword)
    private String uuid;

    @Field(type = FieldType.Search_As_You_Type)
    private String name;

    @Field(type = FieldType.Search_As_You_Type)
    private String email;

    @Field(type = FieldType.Text)
    private String phone;

    @Field(type = FieldType.Keyword)
    private String taxId;
}
