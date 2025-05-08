package com.wechat.ferry.strategy.msg.receive.impl;

import com.wechat.ferry.config.WeChatFerryProperties;
import com.wechat.ferry.entity.dto.WxPpMsgDTO;
import com.wechat.ferry.entity.vo.request.WxPpWcfDownloadAttachReq;
import com.wechat.ferry.entity.vo.response.WxPpWcfContactsResp;
import com.wechat.ferry.entity.vo.response.WxPpWcfGroupMemberResp;
import com.wechat.ferry.enums.ReceiveMsgChannelEnum;
import com.wechat.ferry.enums.ShopManager;
import com.wechat.ferry.enums.WcfMsgTypeEnum;
import com.wechat.ferry.service.WeChatDllService;
import com.wechat.ferry.service.impl.OcrServiceImpl;
import com.wechat.ferry.strategy.msg.receive.ReceiveMsgStrategy;
import com.wechat.ferry.utils.ResourcePath;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 策略实现类-接收消息-签到处理
 *
 * @author chandler
 * @date 2024-12-25 14:19
 */
@Slf4j
@Component
public class OcrStrategyImpl implements ReceiveMsgStrategy {

    @Autowired
    private WeChatDllService weChatDllService;

    @Autowired
    private OcrServiceImpl ocrService; // 注入 ocrService

    @Autowired
    private WeChatFerryProperties weChatFerryProperties;

    @Override
    public String getStrategyType() {
        log.debug("[接收消息]-[OCR处理]-匹配到：{}-{}-策略", ReceiveMsgChannelEnum.OCR.getCode(), ReceiveMsgChannelEnum.OCR.getName());
        return ReceiveMsgChannelEnum.OCR.getCode();
    }

    @Override
    public String doHandle(WxPpMsgDTO dto) {
        // TODO 这里写具体的操作
        // 当前是使用的所有策略类全部执行 所以这里需要控制哪种类型才处理
        // 判断当前群聊是否是店铺群
        log.info("OCR 处理");
        // 发消息


//        weChatDllService.sendTextMsg("", "", null, false);

        // 群ID获取群成员的ID和名称
//        List<WxPpWcfGroupMemberResp> list = weChatDllService.queryGroupMemberList(dto.getRoomId());
//        for (int i = 0; i < list.size(); i++) {
//            WxPpWcfGroupMemberResp resp = list.get(i);
//            log.info("1222,getGroupNickName:{}， getWeChatUid：{}", resp.getGroupNickName(), resp.getWeChatUid());
//            if (Objects.equals(resp.getWeChatUid(), dto.getSender())) {
//                String nickname = resp.getGroupNickName();
//                System.out.println(nickname);
//                break;
//            };
//        }
//
//        WxPpWcfDownloadAttachReq request = new WxPpWcfDownloadAttachReq();
//        request.setMsgId(dto.getId().toString());
//        request.setExtra(dto.getExtra());
//        request.setSavePath("C:\\Users\\Administrator\\Desktop\\WeChatBot\\Java\\dp\\WeChatFerry-dp\\Asset\\TempPic");
//        request.setTimeout(30);
//        request.setThumbnailUrl(dto.getThumb());
//        request.setFileType(".mp4");
//        String pic_path = weChatDllService.downloadVideo(request);
//        System.out.println(pic_path);

        if (ShopManager.isDp(dto.getRoomId()) && ShopManager.isHuiKuanRen(dto.getSender())) {
            if (WcfMsgTypeEnum.PICTURE.getCode().equals(dto.getType().toString())) {
//                log.info("这是可以ocr的群");


                // 获取图片地址


                WxPpWcfDownloadAttachReq request = new WxPpWcfDownloadAttachReq();
                request.setMsgId(dto.getId().toString());
                request.setThumbnailUrl(dto.getThumb());
                request.setExtra(dto.getExtra());
                request.setSavePath(weChatFerryProperties.getFileSavePath() + "\\TempPic");
                request.setFileType(".png");
                request.setTimeout(30);
                String pic_path = weChatDllService.downloadImage(request);
                System.out.println(pic_path);


                // 群ID获取群成员的ID和名称
                List<WxPpWcfContactsResp> list = weChatDllService.queryContactsList();
                String nickname = null;
                for (WxPpWcfContactsResp resp : list) {
//                    log.info("1222,getGroupNickName:{}， getWeChatUid：{}", resp.getWeChatNickname(), resp.getWeChatUid());
                    if (Objects.equals(resp.getWeChatUid(), dto.getRoomId())) {
                        nickname = resp.getWeChatNickname();
                        break;
                    }
                    ;
                }

                // 设置截图地址
                ocrService.setImagePath(pic_path);

                // 执行ocr程序
                ocrService.ocr(dto.getRoomId(), nickname);
            }
        }

        return "";
    }

}
