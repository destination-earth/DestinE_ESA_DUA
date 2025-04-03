package com.exprivia.dfda.duatiadocumentretriever.business.docretriever.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

import org.springframework.stereotype.Component;

@Component
public class DataUtil {

    public String decompress(String encodedString) throws IOException {
        if (encodedString == null) {
            return null;
        }

        byte [] decodedInput = Base64.getDecoder().decode(encodedString);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(decodedInput));
        gzip.transferTo(out);

        return new String(out.toByteArray());
    }
}
