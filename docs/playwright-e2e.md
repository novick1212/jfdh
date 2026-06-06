# Playwright 本地 UI 测试

## 运行环境

- Node.js（你本机已安装）
- Java 11（建议用 Temurin 11）
- Maven

## 安装依赖

```bash
cd /Users/novick/Desktop/jf/points-exchange-system
npm i
npm run e2e:install
```

## 运行 UI 测试

```bash
cd /Users/novick/Desktop/jf/points-exchange-system
npm run e2e
```

测试会自动启动本地 Spring Boot 服务（使用内存 H2，create-drop，端口 8099），跑完自动退出。

## 查看报告

```bash
cd /Users/novick/Desktop/jf/points-exchange-system
npm run e2e:report
```

