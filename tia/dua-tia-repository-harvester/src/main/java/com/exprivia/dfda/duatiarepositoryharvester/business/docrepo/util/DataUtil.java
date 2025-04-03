package com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DataUtil {

    public String compress(String str) {
        if (str == null) {
            return null;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            GZIPOutputStream gzip = new GZIPOutputStream(out);
            gzip.write(str.getBytes());
            gzip.close();
            String ret = new String(Base64.getEncoder().encode(out.toByteArray()));

            log.debug("string size {}, compressed size {}", str.getBytes().length, ret.length());
            return ret;
        } catch (IOException e) {
            log.error("cannot compress input string", e);
            return null;
        }
    }
}
