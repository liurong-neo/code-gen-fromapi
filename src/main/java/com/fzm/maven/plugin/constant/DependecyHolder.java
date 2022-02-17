package com.fzm.maven.plugin.constant;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DependecyHolder {

    public static Map<String, Map<String, List<String>>> currDepencyWithService = new HashMap<>();   //一个service 下对应多个path 一个path 有多个Method

}
