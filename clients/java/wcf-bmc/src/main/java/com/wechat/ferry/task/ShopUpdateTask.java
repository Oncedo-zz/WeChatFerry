//package com.wechat.ferry.task;
//
//import com.wechat.ferry.entity.dto.ShopInfoDTO;
//import com.wechat.ferry.service.shopInfo.ShopInfoService;
//import com.wechat.ferry.service.shopInfo.ShopSyncService;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//import java.util.List;
//
//@Slf4j
//@Component
//public class ShopUpdateTask {
//    private final ShopSyncService shopSyncService;
//    private final ShopInfoService shopInfoService;
//
//    public ShopUpdateTask(ShopSyncService shopSyncService, ShopInfoService shopInfoService) {
//        this.shopSyncService = shopSyncService;
//        this.shopInfoService = shopInfoService;
//    }
//
//    @Scheduled(cron = "${task.shop-update.cron}")
//    public void autoUpdateShops() {
//        try {
//            // 1. 同步数据
//            shopSyncService.syncShopData();
//            // 2. 获取处理后的DTO列表
//            List<ShopInfoDTO> shopList = shopSyncService.getProcessedShopData();
//            // 3. 存储到本地或数据库
//            shopInfoService.updateShopInfo(shopList);
//            log.info("店铺数据更新任务完成");
//        } catch (Exception e) {
//            log.error("定时任务执行失败: {}", e.getMessage(), e);
//        }
//    }
//}
