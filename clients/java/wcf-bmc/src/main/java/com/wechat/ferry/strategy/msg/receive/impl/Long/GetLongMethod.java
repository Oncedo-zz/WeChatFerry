// 文件名称: GetLongMethod.java
package com.wechat.ferry.strategy.msg.receive.impl.Long;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GetLongMethod {
    private final GetLongModule getLongModule;
    private static final Logger log = LoggerFactory.getLogger(GetLongMethod.class);

    @Autowired
    public GetLongMethod(GetLongModule getLongModule) {
        this.getLongModule = getLongModule;
    }

    /**
     * 接龙功能主入口（改为实例方法）
     */
    public void jieLong(WxMsg msg) {
        try {

            getLongModule.processMessage(msg);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("处理被中断", e);
        }
    }
}
