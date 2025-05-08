package com.wechat.ferry.strategy.msg.receive.impl;

import com.wechat.ferry.entity.dto.WxPpMsgDTO;
import com.wechat.ferry.enums.ReceiveMsgChannelEnum;
import com.wechat.ferry.enums.ShopManager;
import com.wechat.ferry.enums.WcfMsgTypeEnum;
import com.wechat.ferry.service.impl.NongHangServiceImpl;
import com.wechat.ferry.strategy.msg.receive.ReceiveMsgStrategy;
import com.wechat.ferry.strategy.msg.receive.impl.Long.GetLongMethod;
import com.wechat.ferry.strategy.msg.receive.impl.Long.WxMsg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 策略实现类-接收消息-农行转账处理
 *
 * @author chandler
 * @date 2024-12-25 14:19
 */
@Slf4j
@Component
public class NongHangStrategyImpl implements ReceiveMsgStrategy {
    // 声明并注入 GetLongMethod
    @Autowired
    private NongHangServiceImpl nongHangServiceImpl;

    @Override
    public String getStrategyType() {
        log.debug("[接收消息]-[农行转账处理]-匹配到：{}-{}-策略", ReceiveMsgChannelEnum.NHZZ.getCode(), ReceiveMsgChannelEnum.NHZZ.getName());
        return ReceiveMsgChannelEnum.NHZZ.getCode();
    }

    @Override
    public String doHandle(WxPpMsgDTO dto) {
        // TODO 这里写具体的操作
        // 当前是使用的所有策略类全部执行 所以这里需要控制哪种类型才处理

        // 只有店铺群才执行
        //System.out.println("是否是店铺和汇款人：" + ShopManager.isDp(dto.getRoomId()) + ShopManager.isHuiKuanRen(dto.getSender()));
        //System.out.println("消息类型：" + WcfMsgTypeEnum.SHARE.getCode().equals(dto.getType().toString()));
        if (ShopManager.isDp(dto.getRoomId()) && ShopManager.isHuiKuanRen(dto.getSender())) {
            if (WcfMsgTypeEnum.SHARE.getCode().equals(dto.getType().toString())) {
                log.info("[转账消息处理]: {}", dto.getContent());
                try {
                    String pinpai = "";
                    //System.out.println("所有街头男人帮店铺：" + ShopManager.getDps_jtnrb());
                    //System.out.println("当前店铺roomid:" + dto.getRoomId());
                    if (ShopManager.getDps_yhgz().contains(dto.getRoomId())) {
                        pinpai = "衣号港仔";
                    } else {
                        pinpai = "街头男人帮";
                    }
                    nongHangServiceImpl.getNongHangJe(dto.getJsonContent(), dto.getRoomId(), pinpai); // 调用实例方法
                } catch (Exception e) {
                    log.error("转账消息处理失败: {}", e.getMessage());
                }
            }
        }
        return "";
    }
}
