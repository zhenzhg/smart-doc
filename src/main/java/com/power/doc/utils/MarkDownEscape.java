package com.power.doc.utils;


public class MarkDownEscape {
    public static String escape(String value) {
        if(value != null) {
            String old = value;
            old = old.replace("|", "&#124;");
            return old;
        }
        return value;
    }
}
