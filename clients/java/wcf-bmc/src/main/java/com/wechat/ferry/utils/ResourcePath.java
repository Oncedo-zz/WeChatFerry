package com.wechat.ferry.utils;

import java.io.File;

public class ResourcePath {
    public static String resourcePath(String relativePath) {
        String basePath = System.getProperty("user.dir");
        return basePath + File.separator + relativePath;
    }
}
