package com.fzm.maven.plugin.utils;

public class StringTools {

    public static String getSubstringBetweenFF(String in, String startChar, String endChar){
        if(null == in || "".equals(in)){
            return "";
        }
        if(in.length() < startChar.length()+endChar.length() || in.indexOf(startChar) <0 || in.indexOf(endChar) < 0){
            return in;
        }
        return in.substring(in.indexOf(startChar)+1,in.lastIndexOf(endChar));
    }

}
