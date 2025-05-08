package com.wechat.ferry.entity.vo.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * 请求入参-个微WCF发送文本消息
 *
 * @author chandler
 * @date 2024-10-02 20:33
 */
@Data
@ApiModel(value = "wxPpWcfSendTextMsgReq", description = "个微WCF发送文本消息请求入参")
public class WxPpWcfSendTextMsgReqCover {

   public static WxPpWcfSendTextMsgReq build(String msgText, String recipient, List<String> atUsers, Boolean isAtAll){
       WxPpWcfSendTextMsgReq request = new WxPpWcfSendTextMsgReq();
       request.setRecipient(recipient);
       request.setMsgText(msgText);
       request.setAtUsers(atUsers);
       request.setIsAtAll(isAtAll);
       return request;
   }

    public static WxPpWcfSendTextMsgReq build(String a, String b, String c){
        WxPpWcfSendTextMsgReq request = new WxPpWcfSendTextMsgReq();
        request.setRecipient(a);
        request.setMsgText(b);
        request.setAtUsers(null);
        return request;
    }

}
