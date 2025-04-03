package com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.util;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RestUtil {

public void logRateLimitHeaders(HttpHeaders headers, String prefix) {
        // print response headers containing request ratio information
        for (String headerKey : headers.keySet()) {
            for (String value : headers.getValuesAsList(headerKey)) {
                if (headerKey.startsWith(prefix)) {
                    log.info("response rate limit headers: {} {}", headerKey, value);
                }
            }
        }
    }
}
