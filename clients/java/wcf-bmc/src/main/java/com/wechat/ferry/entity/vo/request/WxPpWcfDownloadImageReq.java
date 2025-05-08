package com.wechat.ferry.entity.vo.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 请求入参-个微WCF下载图片文件
 *
 * @author chandler
 * @date 2024-10-04 23:11
 */
@Data
@ApiModel(value = "wxPpWcfDownloadImageReq", description = "个微WCF下载图片请求入参")
public class WxPpWcfDownloadImageReq {

    /**
     * id (int): 消息中 id
     * extra (str): 消息中的 extra
     * dir (str): 存放图片的目录（目录不存在会出错）
     * timeout (int): 超时时间（秒）
     */
    @NotBlank(message = "消息id不能为空")
    @ApiModelProperty(value = "消息id")
    private String id;

    /**
     * XML报文内容
     */
    @NotBlank(message = "消息中的 extra 不能为空")
    @ApiModelProperty(value = "消息中的 extra")
    private String extra;

    /**
     * 资源路径-要保存图片的路径
     */
    @ApiModelProperty(value = "要保存图片的路径")
    private String resourcePath;

    /**
     * int 超时时间（秒）
     */
    @NotNull(message = "超时时间不能为空")
    @ApiModelProperty(value = "超时时间")
    private int timeout;

}
