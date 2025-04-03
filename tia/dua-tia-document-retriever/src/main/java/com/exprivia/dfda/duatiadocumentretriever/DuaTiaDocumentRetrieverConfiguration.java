package com.exprivia.dfda.duatiadocumentretriever;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Data;

@Component
@Data
public class DuaTiaDocumentRetrieverConfiguration {
    @Value("${dua.tia.documentretriever.download.path}")
    private String downloadPath;

    @Value("${dua.tia.documentretriever.download.max-retry}")
    private int maxRetryCount;

    @Value("${dua.tia.documentretriever.metadata-queue-name}")
    private String metadataQueueName;

    @Value("${dua.tia.documentretriever.unpaywall-fallback-enabled}")
    private boolean unpaywallServiceFallbackEnabled;

    @Value("${dua.tia.documentretriever.unpaywall-metadata-enabled}")
    private boolean unpaywallServiceMetadataEnabled;

    @Value("${dua.tia.documentretriever.unpaywall-url}")
    private String unpaywallServiceFallbackUrl;

    @Value("${dua.tia.documentretriever.unpaywall-email}")
    private String unpaywallServiceFallbackEmail;

    @Value("${dua.tia.documentretriever.cross-ref-metadata-enabled}")
    private boolean crossRefServiceMetadataEnabled;

    @Value("${dua.tia.documentretriever.cross-ref-url}")
    private String crossRefServiceUrl;

    @Value("${dua.tia.documentretriever.open-citations-metadata-enabled}")
    private boolean openCitationsServiceMetadataEnabled;

    @Value("${dua.tia.documentretriever.open-citations-url}")
    private String openCitationsServiceUrl;
}
