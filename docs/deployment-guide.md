# GovCMS 部署手册

## 环境要求

- 操作系统：麒麟/统信/其他国产 OS
- JDK：OpenJDK 17
- 数据库：达梦 DM8
- 缓存：Redis 6+
- Web 服务器：Nginx 1.20+

## 1. 数据库准备

### 1.1 安装达梦 DM8

参照达梦官方安装手册完成数据库安装。

### 1.2 创建数据库和用户

```sql
CREATE DATABASE GOVCMS1;
CREATE USER GOVCMS1 IDENTIFIED BY 'YourSecurePassword';
GRANT DBA TO GOVCMS1;
```

### 1.3 初始化 Quartz 表

应用首次启动时会自动创建 Quartz 表（`initialize-schema: always` 仅在首次启用）。

## 2. 应用部署

### 2.1 目录结构

```
/opt/govcms/
├── govcms-admin-0.0.1-SNAPSHOT.jar
└── application-prod.yml (可选，如需要覆盖默认配置)

/var/govcms/storage/
├── media/
└── publish/

/var/www/govcms-admin/
└── (前端构建产物)
```

### 2.2 环境变量

编辑 `/opt/govcms/env`：

```bash
export PROD_DB_URL=jdbc:dm://localhost:5236/GOVCMS1
export PROD_DB_USER=GOVCMS1
export PROD_DB_PASSWORD=YourSecurePassword
export GM_SM2_PRIVATE_KEY=your_sm2_private_key_hex
export GM_SM2_PUBLIC_KEY=your_sm2_public_key_hex
export GM_SM4_KEY=your_32_char_hex_key
export REDIS_HOST=localhost
export REDIS_PORT=6379
```

### 2.3 启动应用

```bash
chmod +x scripts/deploy.sh
source /opt/govcms/env
scripts/deploy.sh start
```

验证：
```bash
curl http://localhost:8080/api/health
curl http://localhost:8080/api/auth/login -X POST -d '{"username":"admin","password":"admin123"}' -H "Content-Type: application/json"
```

## 3. Nginx 配置

```bash
cp nginx.conf /etc/nginx/nginx.conf
nginx -t
systemctl reload nginx
```

## 4. 前端部署

```bash
cd frontend
npm install
npm run build
rm -rf /var/www/govcms-admin/*
cp -r dist/* /var/www/govcms-admin/
```

## 5. 备份

```bash
chmod +x scripts/backup.sh
scripts/backup.sh
```

## 6. 回滚

1. 停止应用：`scripts/deploy.sh stop`
2. 恢复数据库：使用达梦备份工具还原
3. 恢复存储：`tar xzf /backup/govcms/YYYYMMDD_HHMMSS/storage.tar.gz -C /var/govcms/storage`
4. 恢复 jar：`cp /backup/govcms/YYYYMMDD_HHMMSS/govcms-admin-0.0.1-SNAPSHOT.jar /opt/govcms/`
5. 启动应用：`scripts/deploy.sh start`

## 7. 国密密钥生成

如需重新生成 SM2 密钥对：

```java
// 在应用中临时执行
@Autowired GmCryptoService gmCryptoService;
KeyPair kp = gmCryptoService.generateSm2KeyPair();
System.out.println("Private: " + bytesToHex(kp.getPrivate().getEncoded()));
System.out.println("Public: " + bytesToHex(kp.getPublic().getEncoded()));
```

SM4 密钥：随机生成 16 字节（32 字符 hex）。
