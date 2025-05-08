// 文件名称: WxMsg.java
package com.wechat.ferry.strategy.msg.receive.impl.Long;

import com.wechat.ferry.entity.dto.WxPpMsgDTO;

/**
 * 微信消息封装类
 */
public class WxMsg {
    private String content;    // 消息内容
    private String roomId;    // 群ID
    private String sender;    // 发送者ID
    private boolean isSelf;   // 是否是自己发送的消息
    private WxPpMsgDTO dto;   // 原始DTO对象

    public WxMsg(String content, String roomId, String sender, WxPpMsgDTO dto) {
        this.content = content;
        this.roomId = roomId;
        this.sender = sender;
        this.isSelf = dto.getIsSelf(); // 假设WxPpMsgDTO中有isSelf属性
        this.dto = dto;
    }

    // Getter 方法
    public String getContent() {
        return content;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getSender() {
        return sender;
    }

    public boolean getIsSelf() {
        return isSelf;
    }

    public WxPpMsgDTO getDto() {
        return dto;
    }
}
