package com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.util;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.springframework.stereotype.Component;

@Component
public class DateUtil {
    private SimpleDateFormat sdfYyyyMmDd = new SimpleDateFormat("yyyy-MM-dd");

    public String formatYyyyMmDd(Date date) {
        if (date == null)
            return "null";
        else
            return sdfYyyyMmDd.format(date);
    }

}
