package com.novick.points.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.novick.points.config.AppProperties;
import com.novick.points.domain.PointsTransaction;
import com.novick.points.domain.RewardItem;
import com.novick.points.domain.SiteConfig;
import com.novick.points.domain.TransactionType;
import com.novick.points.domain.UserAccount;
import com.novick.points.domain.UserRole;
import com.novick.points.repository.PointsTransactionRepository;
import com.novick.points.repository.RewardItemRepository;
import com.novick.points.repository.SiteConfigRepository;
import com.novick.points.repository.UserAccountRepository;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final String OLD_HERO_PROMPT =
            "端午节活动横幅，龙舟与粽子元素，中国风红金配色，企业福利宣传海报，mobile hero banner, festive and elegant";
    private static final String NEW_HERO_PROMPT =
            "端午节手机端活动头图，淡雅青绿色中国风，粽叶与粽子礼盒摆拍，少量金色点缀，画面简洁高级，适合企业员工福利页面，mobile banner, realistic and elegant";

    private final AppProperties appProperties;
    private final AuthService authService;
    private final UserAccountRepository userAccountRepository;
    private final RewardItemRepository rewardItemRepository;
    private final PointsTransactionRepository pointsTransactionRepository;
    private final SiteConfigRepository siteConfigRepository;

    public DataInitializer(AppProperties appProperties, AuthService authService,
            UserAccountRepository userAccountRepository, RewardItemRepository rewardItemRepository,
            PointsTransactionRepository pointsTransactionRepository, SiteConfigRepository siteConfigRepository) {
        this.appProperties = appProperties;
        this.authService = authService;
        this.userAccountRepository = userAccountRepository;
        this.rewardItemRepository = rewardItemRepository;
        this.pointsTransactionRepository = pointsTransactionRepository;
        this.siteConfigRepository = siteConfigRepository;
    }

    @Override
    public void run(String... args) {
        if (userAccountRepository.count() == 0) {
            UserAccount admin = new UserAccount();
            admin.setUsername(appProperties.getSeedAdminUsername());
            admin.setPasswordHash(authService.encode(appProperties.getSeedAdminPassword()));
            admin.setDisplayName("系统管理员");
            admin.setRole(UserRole.ADMIN);
            admin.setPointsBalance(0);
            userAccountRepository.save(admin);

            UserAccount demoUser = new UserAccount();
            demoUser.setUsername(appProperties.getSeedUserUsername());
            demoUser.setPasswordHash(authService.encode(appProperties.getSeedUserPassword()));
            demoUser.setDisplayName("演示用户");
            demoUser.setPhoneNumber(appProperties.getSeedUserPhone());
            demoUser.setHrCode("HR0001");
            demoUser.setRole(UserRole.USER);
            demoUser.setRedeemQuota(1);
            demoUser.setContactName("演示用户");
            demoUser.setContactPhone(appProperties.getSeedUserPhone());
            demoUser.setContactAddress("四川省成都市高新区天府大道 100 号");
            userAccountRepository.save(demoUser);

            PointsTransaction transaction = new PointsTransaction();
            transaction.setUserId(demoUser.getId());
            transaction.setType(TransactionType.INITIAL);
            transaction.setChangeAmount(1200);
            transaction.setBalanceAfter(1200);
            transaction.setSource("系统初始化");
            transaction.setNote("初始赠送积分");
            transaction.setCreatedAt(LocalDateTime.now());
            pointsTransactionRepository.save(transaction);
        }

        userAccountRepository.findByUsername(appProperties.getSeedUserUsername()).ifPresent(user -> {
            if (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
                user.setPhoneNumber(appProperties.getSeedUserPhone());
            }
            if (user.getHrCode() == null || user.getHrCode().isBlank()) {
                user.setHrCode("HR0001");
            }
            if (user.getContactName() == null || user.getContactName().isBlank()) {
                user.setContactName(user.getDisplayName());
            }
            if (user.getContactPhone() == null || user.getContactPhone().isBlank()) {
                user.setContactPhone(user.getPhoneNumber());
            }
            if (user.getContactAddress() == null || user.getContactAddress().isBlank()) {
                user.setContactAddress("请在“我的”页面维护收货地址");
            }
            user.setRedeemQuota(1);
            userAccountRepository.save(user);
        });

        if (rewardItemRepository.count() == 0) {
            rewardItemRepository.save(createItem("端午安康礼盒", "经典粽香礼盒，适合节日关怀发放", 0,
                    createImageUrl("端午节礼盒海报，粽子礼盒，红金中国风，桌面摆拍，节日福利宣传图，mobile banner, premium lighting"), 50, 1));
            rewardItemRepository.save(createItem("健康养生礼包", "偏实用型方案，适合日常健康关怀", 0,
                    createImageUrl("端午健康礼包，绿豆薏米香囊组合，中国风礼盒，清新绿色，realistic product photo"), 50, 2));
            rewardItemRepository.save(createItem("国风茶礼套装", "茶礼与节令文化结合，适合商务和家用", 0,
                    createImageUrl("端午国风茶礼盒，龙舟元素，中国风茶具礼盒，深色木桌，high-end product shot"), 50, 3));
            rewardItemRepository.save(createItem("甄选水果礼箱", "应季水果搭配节日礼赠场景", 0,
                    createImageUrl("端午水果礼箱，荔枝樱桃礼盒，节日福利宣传图，fresh realistic food photography"), 50, 4));
            rewardItemRepository.save(createItem("家居实用套装", "兼顾节日与实用性的综合福利方案", 0,
                    createImageUrl("节日家居礼品套装，保温杯毛巾香薰组合，简洁高级，ecommerce product image"), 50, 5));
            rewardItemRepository.save(createItem("亲子欢乐组合", "面向家庭场景的暖心福利方案", 0,
                    createImageUrl("端午亲子礼盒，卡通粽子和儿童用品，温暖家庭氛围，bright realistic photo"), 50, 6));
            rewardItemRepository.save(createItem("尊享精选方案", "适合高关注度人群的精品节日礼遇", 0,
                    createImageUrl("高端端午福利礼盒，红金礼盒与粽子，企业节日海报，luxury realistic banner"), 50, 7));
        }

        if (!siteConfigRepository.existsById(SiteConfig.DEFAULT_ID)) {
            SiteConfig config = new SiteConfig();
            config.setId(SiteConfig.DEFAULT_ID);
            config.setAnnouncementHtml("<p><strong>端午福利说明</strong></p><p>本次活动每位员工仅可在 7 个方案中选择 1 个进行兑换，请先确认收货信息后再提交。</p>");
            config.setHeroImageUrl(defaultHeroImageUrl());
            config.setHotline("400-800-2026");
            siteConfigRepository.save(config);
        } else {
            siteConfigRepository.findById(SiteConfig.DEFAULT_ID).ifPresent(config -> {
                String currentHero = config.getHeroImageUrl();
                if (currentHero == null || currentHero.isBlank() || currentHero.equals(oldHeroImageUrl())) {
                    config.setHeroImageUrl(defaultHeroImageUrl());
                    siteConfigRepository.save(config);
                }
            });
        }
    }

    private RewardItem createItem(String name, String description, int pointsCost, String coverImage, int stock,
            int sortOrder) {
        RewardItem item = new RewardItem();
        item.setName(name);
        item.setDescription(description);
        item.setPointsCost(pointsCost);
        item.setStock(stock);
        item.setCoverImage(coverImage);
        item.setActive(true);
        item.setSortOrder(sortOrder);
        return item;
    }

    private String createImageUrl(String prompt) {
        return "https://coresg-normal.trae.ai/api/ide/v1/text_to_image?prompt="
                + URLEncoder.encode(prompt, StandardCharsets.UTF_8)
                + "&image_size=landscape_16_9";
    }

    private String defaultHeroImageUrl() {
        return createImageUrl(NEW_HERO_PROMPT);
    }

    private String oldHeroImageUrl() {
        return createImageUrl(OLD_HERO_PROMPT);
    }
}
