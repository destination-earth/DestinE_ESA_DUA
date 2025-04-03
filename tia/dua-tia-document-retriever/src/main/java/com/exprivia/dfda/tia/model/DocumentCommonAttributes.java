package com.exprivia.dfda.tia.model;

import java.util.Date;
import java.util.List;

import org.springframework.lang.Nullable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class DocumentCommonAttributes {
    @NonNull
    private String id;

    @NonNull
    private String group;

    private SearchConfiguration configuration;

    @NonNull
    private Repository repository;

    @NonNull
    private Document document;

    @NonNull
    private DownloadInfo downloadInfo;

    @NonNull
    private PublicationDetails publicationDetails;

    @NonNull
    private String rawSearchResultRecord;

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    @Builder
    public static class SearchConfiguration {
        @NonNull
        private String initiative;

        @NonNull
        private String documentType;

        @NonNull
        private String keywordDictionary;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    @Builder
    public static class Repository {
        @NonNull
        private String driver;
        
        @NonNull
        private String url;

        private String credentials;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    @Builder
    public static class Document {
        private List<String> doi;

        @NonNull
        private String title;
    
        private String docAbstract;

        private String fullText;
    
        private String language;
    
        private List<String> authors;
        private List<String> contributors;
        private List<String> editors;
    
        private Date createdOn;
    
        private Date publishedOn;

        private List<Citation> citations;

        private String keywordDictionarySecondLevel;


        @NoArgsConstructor
        @AllArgsConstructor
        @Data
        @Builder
        public static class Citation {
            String type;
            String language;
            String text;
        }
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    @Builder
    public static class DownloadInfo {
        @NonNull
        private List<String> documentUrl;

        private Integer minimumRequired;
        
        @NonNull
        private String downloadDriver;

        private String credentials;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    @Builder
    public static class PublicationDetails {
        @Nullable
        private Boolean isOpenAccess;

        @Nullable
        private PublicationType publicationType;

        @Nullable
        private String publicationTypeString;

        @Nullable
        private Integer citationCount;

        @Nullable
        private List<String> journalTitles;

        @Nullable
        private List<String> journalIssns;

        @Nullable
        private Float journalImpactFactor;

        @Nullable
        private Float repositoryScore;
    }

    public static enum PublicationType {
        JOURNAL_ARTICLE,
        BOOK,
        RESEARCH,
        TECHNICAL_REPORT,
        ERRATA,
        REVIEW,
        DATA,
        LETTER,
        UNKNOWN;

        public static PublicationType fromString(String value) {
            if (value == null) {
                return null;
            }

            String v = value.toLowerCase();

            if (v.contains("article")) return JOURNAL_ARTICLE;
            if (v.contains("book")) return BOOK;

            if (v.contains("research")) return RESEARCH;
            if (v.contains("tech") && v.contains("report")) return TECHNICAL_REPORT;

            if (v.contains("errata")) return ERRATA;
            if (v.contains("review")) return REVIEW;

            if (v.contains("data")) return DATA;

            if (v.contains("letter")) return LETTER;

            return UNKNOWN;
        }
    }
}
