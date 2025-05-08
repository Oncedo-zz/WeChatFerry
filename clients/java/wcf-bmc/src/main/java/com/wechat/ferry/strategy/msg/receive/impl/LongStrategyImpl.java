package com.wechat.ferry.strategy.msg.receive.impl;

import com.wechat.ferry.entity.dto.WxPpMsgDTO;
import com.wechat.ferry.enums.ReceiveMsgChannelEnum;
import com.wechat.ferry.strategy.msg.receive.ReceiveMsgStrategy;
import com.wechat.ferry.strategy.msg.receive.impl.Long.GetLongMethod;
import com.wechat.ferry.strategy.msg.receive.impl.Long.WxMsg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 策略实现类-接收消息-接龙处理
 *
 * @author chandler
 * @date 2024-12-25 14:19
 */
@Slf4j
@Component
public class LongStrategyImpl implements ReceiveMsgStrategy {
    // 声明并注入 GetLongMethod
    @Autowired
    private GetLongMethod getLongMethod;

    @Override
    public String getStrategyType() {
        log.debug("[接收消息]-[接龙处理]-匹配到：{}-{}-策略", ReceiveMsgChannelEnum.LONG.getCode(), ReceiveMsgChannelEnum.LONG.getName());
        return ReceiveMsgChannelEnum.LONG.getCode();
    }

    @Override
    public String doHandle(WxPpMsgDTO dto) {
        // TODO 这里写具体的操作
        // 当前是使用的所有策略类全部执行 所以这里需要控制哪种类型才处理
        log.info("[龙功能]-处理消息: {}", dto.getContent());
        try {
            WxMsg msg = new WxMsg(
                dto.getContent(),
                dto.getRoomId(),
                dto.getSender(),
                dto
            );
            getLongMethod.jieLong(msg); // 调用实例方法
        } catch (Exception e) {
            log.error("处理失败: {}", e.getMessage());
        }

        return "";
    }


}
