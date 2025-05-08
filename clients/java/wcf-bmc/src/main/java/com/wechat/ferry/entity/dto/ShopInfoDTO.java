package com.wechat.ferry.entity.dto;

import lombok.Data;
import java.util.List;

/**
 * 店铺信息数据传输对象
 * 用于在各层之间传递店铺数据
 */
@Data
public class ShopInfoDTO {
    /**
     * 店铺唯一标识
     */
    private String roomId;

    /**
     * 店铺名称
     */
    private String shopName;

    /**
     * 店铺所属区域
     * */
    private String shopArea;
    /**
     * 负责人列表
     */
    private List<String> bosses;

    /**
     * 营业状态
     * 0: 正常营业 1: 暂停营业
     */
    private Integer status;

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getShopName() {
        return shopName;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }

    public List<String> getBosses() {
        return bosses;
    }

    public void setBosses(List<String> bosses) {
        this.bosses = bosses;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

//    public ShopInfoDTO(String roomId, String shopName, List<String> bosses, Integer status) {
//        this.roomId = roomId;
//        this.shopName = shopName;
//        this.bosses = bosses;
//        this.status = status;
//    }
}
