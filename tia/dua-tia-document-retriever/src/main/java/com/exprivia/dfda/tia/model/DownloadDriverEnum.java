package com.exprivia.dfda.tia.model;

public enum DownloadDriverEnum {
    SIMPLE_DOWNLOADER,
    SCOPUS_DOWNLOADER;

    public static DownloadDriverEnum fromString(String text) {
        if (SIMPLE_DOWNLOADER.name().equals(text))
            return SIMPLE_DOWNLOADER;
        if (SCOPUS_DOWNLOADER.name().equals(text))
            return SCOPUS_DOWNLOADER;
        
        throw new RuntimeException("unrecognized download driver " + text);
    }
}
