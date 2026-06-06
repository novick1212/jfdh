# 项目交付与服务器部署操作文档

## 1. 交付清单

- 源码压缩包
  - `points-exchange-system/`（后端 + 静态前端 + Dockerfile + 测试）
  - `docs/`（部署/迁移/测试文档）
- 数据（如果继续使用 H2 文件库）
  - `points-exchange-system/data/points-exchange.mv.db`
  - 如果存在：`points-exchange-system/data/points-exchange.trace.db`
- 部署文档
  - 本文档：`docs/deploy-handoff.md`
  - 数据迁移文档（如果上 MySQL）：`docs/mysql-migration.md`

不建议把任何第三方密钥写进代码或发到群里：

- 快递100：`KUAIDI100_CUSTOMER`、`KUAIDI100_KEY`
- 短信平台的 `AK/SK`（未来接入时）

### 1.1 如何在本机生成“源码交付包”（macOS 示例）

在你的电脑上执行（会生成 `points-exchange-system-source.zip`）：

```bash
cd /Users/novick/Desktop/jf

zip -r points-exchange-system-source.zip points-exchange-system docs \
  -x "*/target/*" "*/node_modules/*" "*/.idea/*" "*/.DS_Store" "*/.classpath" "*/.project" "*/.settings/*"
```

如果你要把现有 H2 数据一并交付，请确认压缩包内包含：

- `points-exchange-system/data/points-exchange.mv.db`

## 2. 技术栈与最低要求

- Java：11（推荐 Temurin 11）
- Maven：3.9+
- 运行方式二选一：
  - Docker（推荐）
  - 直接运行 jar（需要 systemd 或类似守护）
- 数据库二选一：
  - H2 文件库（最省事，但不适合多实例）
  - MySQL 5.7（推荐生产）

## 3. 环境变量（部署必须配置）

### 3.1 服务端口

- `PORT`：服务监听端口，默认 `8080`

### 3.2 数据库（H2 或 MySQL 二选一）

H2 文件库（默认配置，示例）：

- `SPRING_DATASOURCE_URL=jdbc:h2:file:./data/points-exchange;MODE=MYSQL;DB_CLOSE_DELAY=-1`
- `SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver`
- `SPRING_DATASOURCE_USERNAME=sa`
- `SPRING_DATASOURCE_PASSWORD=`
- `SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.H2Dialect`

MySQL 5.7（示例）：

- `SPRING_DATASOURCE_URL=jdbc:mysql://<host>:3306/<db>?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai`
- `SPRING_DATASOURCE_DRIVER_CLASS_NAME=com.mysql.cj.jdbc.Driver`
- `SPRING_DATASOURCE_USERNAME=<user>`
- `SPRING_DATASOURCE_PASSWORD=<password>`
- `SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.MySQL57Dialect`

建议生产关闭 H2 Console：

- `SPRING_H2_CONSOLE_ENABLED=false`

### 3.3 系统初始化账号（可选）

以下为默认值，仅在“库里没有任何用户数据”时才会创建种子账号：

- `APP_SEED_ADMIN_USERNAME=admin`
- `APP_SEED_ADMIN_PASSWORD=admin123`
- `APP_SEED_USER_USERNAME=demo`
- `APP_SEED_USER_PASSWORD=demo123`
- `APP_SEED_USER_PHONE=13800000000`

### 3.4 短信（当前为 Mock，可选）

生产建议关闭 mock：

- `APP_SMS_MOCK_ENABLED=false`

### 3.5 快递100（如果要展示物流轨迹，必须配置）

用于后端查询物流轨迹（用户端“查看物流”会调用后端接口）：

- `KUAIDI100_CUSTOMER=<快递100 customer>`
- `KUAIDI100_KEY=<快递100 key>`

## 4. 部署方式 A（推荐）：Docker 部署

项目已包含 `points-exchange-system/Dockerfile`，可直接构建并运行。

### 4.1 构建镜像

在服务器上：

```bash
cd points-exchange-system
docker build -t points-exchange-system:latest .
```

### 4.2 运行容器（H2 文件库）

H2 文件库必须挂载持久化目录，否则容器重启会丢数据。

```bash
mkdir -p /srv/points-exchange-system/data

docker run -d --name points-exchange-system \
  -p 8080:8080 \
  -e PORT=8080 \
  -e SPRING_H2_CONSOLE_ENABLED=false \
  -e SPRING_DATASOURCE_URL="jdbc:h2:file:/app/data/points-exchange;MODE=MYSQL;DB_CLOSE_DELAY=-1" \
  -v /srv/points-exchange-system/data:/app/data \
  points-exchange-system:latest
```

如果你要把本地 H2 数据带过去，把 `points-exchange.mv.db` 放入：

```bash
/srv/points-exchange-system/data/points-exchange.mv.db
```

### 4.3 运行容器（MySQL 5.7）

```bash
docker run -d --name points-exchange-system \
  -p 8080:8080 \
  -e PORT=8080 \
  -e SPRING_H2_CONSOLE_ENABLED=false \
  -e SPRING_DATASOURCE_URL="jdbc:mysql://<host>:3306/<db>?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai" \
  -e SPRING_DATASOURCE_DRIVER_CLASS_NAME="com.mysql.cj.jdbc.Driver" \
  -e SPRING_DATASOURCE_USERNAME="<user>" \
  -e SPRING_DATASOURCE_PASSWORD="<password>" \
  -e SPRING_JPA_DATABASE_PLATFORM="org.hibernate.dialect.MySQL57Dialect" \
  -e KUAIDI100_CUSTOMER="<customer>" \
  -e KUAIDI100_KEY="<key>" \
  points-exchange-system:latest
```

## 5. 部署方式 B：直接运行 jar（无 Docker）

### 5.1 构建 jar

在有 Java 11 + Maven 的机器上（推荐服务器本机构建）：

```bash
cd points-exchange-system
mvn clean package
```

产物路径：

- `points-exchange-system/target/points-exchange-system-1.0.0-SNAPSHOT.jar`

### 5.2 运行 jar

```bash
export PORT=8080
export SPRING_H2_CONSOLE_ENABLED=false
java -jar target/points-exchange-system-1.0.0-SNAPSHOT.jar
```

生产建议用 systemd 管理进程，并把环境变量写到 systemd service 文件或单独的 env 文件。

## 6. 数据库方案选择建议

- 只部署单台、数据量不大、想快速上线：H2 文件库可用
- 生产/多人维护/需要更稳：建议 MySQL 5.7，并把现有 H2 数据迁移过去

H2 → MySQL 迁移请按文档执行：

- `docs/mysql-migration.md`

## 7. 访问入口与验收

- 用户端登录页：`http://<host>:<port>/app/login.html`
- 管理后台登录页：`http://<host>:<port>/admin/login.html`

基本验收清单（建议逐条确认）：

- 后台登录成功
- 后台 CSV 导入用户成功
- 后台给某用户设置兑换次数成功
- 用户端：三要素校验后能获取验证码（开发环境会显示 debugCode）
- 用户端：加入购物车 → 结算下单成功
- 后台：订单发货可录入快递单号与快递100公司编码 com
- 用户端：订单里能看到“查看物流”按钮（配置快递100后可看轨迹）
- 后台：导出兑换报表可下载 xlsx

## 8. 本地测试（交付给对方前建议跑一遍）

### 8.1 接口级自动化测试

```bash
cd points-exchange-system
export JAVA_HOME=$(/usr/libexec/java_home -v 11)
export PATH="$JAVA_HOME/bin:$PATH"
mvn test
```

### 8.2 UI 自动化测试（Playwright）

```bash
cd points-exchange-system
npm i
npm run e2e:install
npm run e2e
```

