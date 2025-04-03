package com.exprivia.dfda.duatiadocumentretriever.business.docretriever.util;

import java.io.InputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpHeaders;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.exprivia.dfda.duatiadocumentretriever.DuaTiaDocumentRetrieverConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DownloadUtil {
    private static final int MAX_HOPS = 10;
    private static final String[] DEFAULT_HEADERS = { 
        "User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:126.0) Gecko/20100101 Firefox/126.0" 
    };

    @Autowired
    private DuaTiaDocumentRetrieverConfiguration config;

    public String[] defaultHeaders() {
        return DEFAULT_HEADERS;
    }

    public String downloadFile(URI uri, String localPath, String... headers) throws IOException, InterruptedException {
        boolean moved = false;
        int availableHops = MAX_HOPS;

        HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)     // manually handle the redirection flow
            .build();
        
        URI currentUri = uri;
        HttpResponse<InputStream> httpResponse = null;
        do {
            HttpRequest httpRequest = HttpRequest
                .newBuilder(currentUri)
                .headers(headers)
                .build();
            httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            
            int httpStatus = httpResponse.statusCode();
            int httpStatusClass = httpStatus / 100;
            moved = httpStatusClass == 3;

            if (moved) {
                /*
                 * this piece of code does not take into consideration HTTP<->HTTPS
                 * jumps that can lead to security risks!!
                 */
                currentUri = makeAbsolute(
                    currentUri, 
                    followRedirect(httpResponse.headers()));

                availableHops--;
            } else if (httpStatusClass == 4 || httpStatusClass == 5) {
                throw new IOException("http error " + httpStatus + " returned by url " + currentUri.toString());
            }
        } while (availableHops > 0 && moved);

        if (availableHops == 0 && moved) {
            throw new IOException("maximum number of location redirects reached");
        }

        String contentType = httpResponse.headers().firstValue("Content-Type").orElse(null);
        String contentDisposition = httpResponse.headers().firstValue("Content-Disposition").orElse(null);

        ReadableByteChannel readableByteChannel = Channels.newChannel(httpResponse.body());

        String fullPathName = uri.getPath();
        // set the output filename default as the url path filename part
        String localFileName = localPath + File.separator + fullPathName.substring(fullPathName.lastIndexOf('/') + 1, fullPathName.length());

        if (contentDisposition != null) {
            // server filename exists, set the output filename as suggested by the server
            ContentDisposition cd = ContentDisposition.parse(contentDisposition);
            localFileName = localPath + File.separator + cd.getFilename();
        } 

        File file = new File(localFileName);
        if (contentType != null && contentType.contains("application/pdf")) {
            if (!file.getName().contains(".")) {
                // append the ".pdf" extension if localFileName does not have one
                // and the content-type pf the server file is */pdf
                localFileName += ".pdf";
            } else {
                // replace every other wrong extension with the proper one, if pdf
                localFileName = file.getParentFile().getAbsolutePath() + File.separator + file.getName().replaceAll("\\..+", ".pdf");
            }
        }

        try (FileOutputStream fileOutputStream = new FileOutputStream(localFileName)) {
            FileChannel fileChannel = fileOutputStream.getChannel();
            fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);

            return localFileName;
        }
    }

    public HttpResponse<String> downloadAsString(URI uri, String ... headers) throws IOException, InterruptedException {
        boolean moved = false;
        int availableHops = MAX_HOPS;

        HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

        URI currentUri = uri;
        HttpResponse<String> httpResponse = null;
        do {
            HttpRequest httpRequest = HttpRequest
                .newBuilder(currentUri)
                .headers(headers)
                .build();
            httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int httpStatus = httpResponse.statusCode();
            int httpStatusClass = httpStatus / 100;
            moved = httpStatusClass == 3;
            if (moved) {
                currentUri = makeAbsolute(
                    currentUri, 
                    followRedirect(httpResponse.headers()));

                availableHops--;
            } else if (httpStatusClass == 4 || httpStatusClass == 5) {
                throw new IOException("http error " + httpResponse.statusCode() + " returned by url " + uri.toString());
            }
        } while (availableHops > 0 && moved);

        if (availableHops == 0 && moved) {
            throw new IOException("maximum number of location redirects reached");
        }

        return httpResponse;
    }

    private static URI followRedirect(HttpHeaders httpHeaders) throws IOException {
        Optional<String> locationHeader = httpHeaders.firstValue("Location");
        if (!locationHeader.isPresent()) {
            throw new IOException("redirection http code detected but \"Location\" header is missing");
        }
        String location = locationHeader.get();
        String sanitizedLocation = URLDecoder.decode(location, "UTF-8").replace(" ", "%20");
        log.info("location header value: {}", location);
        log.info("   -> sanitized value: {}", sanitizedLocation);

        // return the sanitized location
        return URI.create(sanitizedLocation);
    }

    public static URI makeAbsolute(URI referenceUri, URI currentUri) {
        if (currentUri.isAbsolute()) {
            return currentUri;
        }

        if (!referenceUri.isAbsolute()) {
            throw new IllegalArgumentException("referenceUri is not absolute");
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(currentUri);
        builder.scheme(referenceUri.getScheme());
        builder.port(referenceUri.getPort());
        builder.host(referenceUri.getHost());

        return builder.build().toUri();
    }

    public JsonNode getJsonNodeFromURI(URI uri) throws IOException {
        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<String> result = restTemplate.getForEntity(uri, String.class);

            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readTree(result.getBody());
        } catch (RestClientException e) {
            throw new IOException("cannot contact json service at " + uri, e);
        }
    }

    public interface ExternalServiceUriResolver {
        public URI resolve(String doi);
        public String getServiceName();
    }

    public class ExternalServiceUriResolverUnpaywall implements ExternalServiceUriResolver {
        private static final String SERVICE_NAME = "unpaywall-metadata";

        public URI resolve(String doi) {
            return URI.create(config.getUnpaywallServiceFallbackUrl() + doi + "?email=" + config.getUnpaywallServiceFallbackEmail());
        }

        public String getServiceName() {
            return SERVICE_NAME;
        }
    }

    public class ExternalServiceUriResolverCrossRef implements ExternalServiceUriResolver {
        private static final String SERVICE_NAME = "cross-ref-metadata";

        public URI resolve(String doi) {
            return URI.create(config.getCrossRefServiceUrl() + doi);
        }

        public String getServiceName() {
            return SERVICE_NAME;
        }
    }

    public class ExternalServiceUriResolverOpenCitations implements ExternalServiceUriResolver {
        private static final String SERVICE_NAME = "open-citations-metadata";

        public URI resolve(String doi) {
            return URI.create(config.getOpenCitationsServiceUrl() + "doi:" + doi);
        }

        public String getServiceName() {
            return SERVICE_NAME;
        }
    }
}
