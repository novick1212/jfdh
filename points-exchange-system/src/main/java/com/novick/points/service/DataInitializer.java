package com.novick.points.service;

import java.time.LocalDateTime;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.novick.points.config.AppProperties;
import com.novick.points.domain.PointsTransaction;
import com.novick.points.domain.RewardItem;
import com.novick.points.domain.TransactionType;
import com.novick.points.domain.UserAccount;
import com.novick.points.domain.UserRole;
import com.novick.points.repository.PointsTransactionRepository;
import com.novick.points.repository.RewardItemRepository;
import com.novick.points.repository.UserAccountRepository;

@Component
public class DataInitializer implements CommandLineRunner {

    private final AppProperties appProperties;
    private final AuthService authService;
    private final UserAccountRepository userAccountRepository;
    private final RewardItemRepository rewardItemRepository;
    private final PointsTransactionRepository pointsTransactionRepository;

    public DataInitializer(AppProperties appProperties, AuthService authService,
            UserAccountRepository userAccountRepository, RewardItemRepository rewardItemRepository,
            PointsTransactionRepository pointsTransactionRepository) {
        this.appProperties = appProperties;
        this.authService = authService;
        this.userAccountRepository = userAccountRepository;
        this.rewardItemRepository = rewardItemRepository;
        this.pointsTransactionRepository = pointsTransactionRepository;
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
            demoUser.setPointsBalance(1200);
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
            userAccountRepository.save(user);
        });

        if (rewardItemRepository.count() == 0) {
            rewardItemRepository.save(createItem("便携保温杯", "适合积分兑换的常用礼品", 299,
                    "/res/default/bg/image/icon/logo.png", 30, 1));
            rewardItemRepository.save(createItem("定制双肩包", "轻办公场景常用周边", 499,
                    "/res/default/bg/image/1.png", 15, 2));
            rewardItemRepository.save(createItem("蓝牙耳机", "作为高价值兑换商品展示完整流程", 899,
                    "/res/default/bg/image/2.png", 8, 3));
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
}
