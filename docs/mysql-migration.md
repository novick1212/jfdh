## 目标

把现有的 H2 文件库数据（含用户、商品、订单、积分流水）迁移到 MySQL 5.7，保证历史数据不丢。

## 迁移前准备

1. 准备一个 MySQL 5.7 数据库（建议新建一个空库作为目标库，比如 `points_exchange`）
2. 记录 MySQL 连接信息：host、端口、库名、用户名、密码
3. 确认你要迁移的 H2 数据来源：
   - 本地：`points-exchange-system/data/points-exchange.mv.db`
   - Render：以 Render 环境变量 `SPRING_DATASOURCE_URL` 对应的文件为准

## 迁移方式（推荐）

使用项目内置的迁移工具（会把 source 的表数据逐表写入 MySQL；并保持原有 id）。

迁移工具会处理 4 张表：
- `user_account`
- `reward_item`
- `exchange_order`
- `points_transaction`

为避免误覆盖，默认要求目标库这 4 张表必须是空的；若目标库已存在数据，需要显式开启覆盖参数。

## 本地 H2 → MySQL 5.7

在 `points-exchange-system` 目录执行：

### 方式 A：直接运行 jar（推荐）

```bash
mvn -DskipTests package

SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:3306/points_exchange?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai" \
SPRING_DATASOURCE_USERNAME="root" \
SPRING_DATASOURCE_PASSWORD="你的密码" \
SPRING_DATASOURCE_DRIVER_CLASS_NAME="com.mysql.cj.jdbc.Driver" \
SPRING_JPA_DATABASE_PLATFORM="org.hibernate.dialect.MySQL57Dialect" \
java -jar target/points-exchange-system-1.0.0-SNAPSHOT.jar \
  --spring.main.web-application-type=none \
  --spring.profiles.active=db-migrate \
  --migrate.enabled=true \
  --migrate.source.url="jdbc:h2:file:./data/points-exchange;MODE=MYSQL;DB_CLOSE_DELAY=-1" \
  --migrate.source.username=sa \
  --migrate.source.password=
```

### 方式 B：spring-boot:run 指定 main-class

```bash
SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:3306/points_exchange?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai" \
SPRING_DATASOURCE_USERNAME="root" \
SPRING_DATASOURCE_PASSWORD="你的密码" \
SPRING_DATASOURCE_DRIVER_CLASS_NAME="com.mysql.cj.jdbc.Driver" \
SPRING_JPA_DATABASE_PLATFORM="org.hibernate.dialect.MySQL57Dialect" \
mvn -DskipTests spring-boot:run \
  -Dspring-boot.run.main-class=com.novick.points.tools.DbMigrateApplication \
  -Dspring-boot.run.arguments="--migrate.enabled=true --migrate.source.url=jdbc:h2:file:./data/points-exchange;MODE=MYSQL;DB_CLOSE_DELAY=-1 --migrate.source.username=sa --migrate.source.password="
```

### 如果目标库已有数据（谨慎）

```bash
... \
  --migrate.allowOverwrite=true
```

## Render H2 → MySQL 5.7

### 1) 从 Render 导出 H2 数据库文件

1. 在 Render 服务面板进入 Shell
2. 根据 `SPRING_DATASOURCE_URL` 找到 H2 文件实际路径（例如：`jdbc:h2:file:/var/data/points-exchange...`）
3. 打包并输出 base64（避免二进制直传失败）：

```bash
tar -czf /tmp/h2db.tgz /var/data/points-exchange.mv.db
base64 /tmp/h2db.tgz > /tmp/h2db.tgz.b64
wc -c /tmp/h2db.tgz.b64
head -n 5 /tmp/h2db.tgz.b64
```

4. 把 `/tmp/h2db.tgz.b64` 全量复制到本地保存为 `h2db.tgz.b64`，然后在本地还原：

```bash
base64 -d h2db.tgz.b64 > h2db.tgz
tar -xzf h2db.tgz
```

你会得到 `points-exchange.mv.db`（或者带路径的同名文件）。

### 2) 本地执行迁移到 MySQL

把 `--migrate.source.url` 改为你本地还原后的文件路径即可，例如：

```bash
--migrate.source.url="jdbc:h2:file:/绝对路径/points-exchange;MODE=MYSQL;DB_CLOSE_DELAY=-1"
```

## 迁移后验证

1. 用 MySQL 客户端检查 4 张表数据量是否符合预期
2. 把应用切到 MySQL（在 Render 上配置同一套 MySQL 环境变量），启动后随机抽查：
   - 管理端登录
   - 商品列表与库存
   - 订单列表与发货信息
   - 用户兑换次数（redeemQuota / redeemUsed）

