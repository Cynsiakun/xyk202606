package com.cd.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class AssetModuleInitializer {

    private final JdbcTemplate jdbcTemplate;

    @Bean
    @Order(1)
    public ApplicationRunner initAssetModule() {
        return args -> {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS accounts (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        task_id VARCHAR(64),
                        host_name VARCHAR(255),
                        mac_address VARCHAR(64),
                        source VARCHAR(16) NOT NULL DEFAULT 'PLATFORM',
                        asset_count INT,
                        asset_json LONGTEXT,
                        deleted TINYINT DEFAULT 0,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    )
                    """);

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS services (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        task_id VARCHAR(64),
                        host_name VARCHAR(255),
                        mac_address VARCHAR(64),
                        source VARCHAR(16) NOT NULL DEFAULT 'PLATFORM',
                        asset_count INT,
                        asset_json LONGTEXT,
                        deleted TINYINT DEFAULT 0,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    )
                    """);

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS processes (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        task_id VARCHAR(64),
                        host_name VARCHAR(255),
                        mac_address VARCHAR(64),
                        source VARCHAR(16) NOT NULL DEFAULT 'PLATFORM',
                        asset_count INT,
                        asset_json LONGTEXT,
                        deleted TINYINT DEFAULT 0,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    )
                    """);

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS apps (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        task_id VARCHAR(64),
                        host_name VARCHAR(255),
                        mac_address VARCHAR(64),
                        source VARCHAR(16) NOT NULL DEFAULT 'PLATFORM',
                        asset_count INT,
                        asset_json LONGTEXT,
                        deleted TINYINT DEFAULT 0,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    )
                    """);

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS port_scan_results (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL DEFAULT 0,
                        task_id VARCHAR(64),
                        host_name VARCHAR(255),
                        mac_address VARCHAR(64),
                        source VARCHAR(16) NOT NULL DEFAULT 'PLATFORM',
                        port_count INT,
                        port_json LONGTEXT,
                        deleted TINYINT DEFAULT 0,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        KEY idx_port_scan_mac (mac_address),
                        KEY idx_port_scan_task (task_id),
                        KEY idx_port_scan_tenant (tenant_id)
                    )
                    """);

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS host_asset_inventory (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        host_id BIGINT NOT NULL,
                        mac_address VARCHAR(64) DEFAULT NULL,
                        category VARCHAR(32) NOT NULL,
                        sub_category VARCHAR(64) DEFAULT NULL,
                        vendor VARCHAR(100) DEFAULT NULL,
                        product VARCHAR(255) NOT NULL,
                        product_version VARCHAR(255) DEFAULT NULL,
                        protocol VARCHAR(16) DEFAULT 'tcp',
                        port INT DEFAULT NULL,
                        rule_id BIGINT DEFAULT NULL,
                        confidence INT DEFAULT 80,
                        first_seen DATETIME DEFAULT NULL,
                        last_seen DATETIME DEFAULT NULL,
                        raw_banner TEXT DEFAULT NULL,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        deleted TINYINT DEFAULT 0,
                        tenant_id BIGINT NOT NULL DEFAULT 0,
                        task_id VARCHAR(64) DEFAULT NULL,
                        source VARCHAR(16) NOT NULL DEFAULT 'PLATFORM',
                        KEY idx_inventory_host (host_id),
                        KEY idx_inventory_host_cat (host_id, category),
                        KEY idx_inventory_product (product),
                        KEY idx_inventory_mac (mac_address)
                    )
                    """);

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS asset_fingerprint_rule (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        rule_code VARCHAR(64) NOT NULL,
                        name VARCHAR(255) NOT NULL,
                        category VARCHAR(32) NOT NULL,
                        sub_category VARCHAR(64) DEFAULT NULL,
                        protocol VARCHAR(16) DEFAULT 'tcp',
                        port INT DEFAULT NULL,
                        banner_regex VARCHAR(500) DEFAULT NULL,
                        vendor VARCHAR(100) DEFAULT NULL,
                        product VARCHAR(255) NOT NULL,
                        version_expr VARCHAR(255) DEFAULT NULL,
                        confidence INT DEFAULT 80,
                        description VARCHAR(2000) DEFAULT NULL,
                        enabled TINYINT DEFAULT 1,
                        priority INT DEFAULT 100,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_asset_fingerprint_rule_code (rule_code),
                        KEY idx_fingerprint_enabled (enabled),
                        KEY idx_fingerprint_category (category),
                        KEY idx_fingerprint_port (port)
                    )
                    """);

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS mq_error_logs (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        queue_name VARCHAR(128),
                        raw_message LONGTEXT,
                        error_reason LONGTEXT,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        tenant_id BIGINT NOT NULL DEFAULT 0
                    )
                    """);

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS agent_result_operation (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL DEFAULT 0,
                        operation_id VARCHAR(128) NOT NULL,
                        type VARCHAR(32) NOT NULL,
                        host_name VARCHAR(255),
                        mac_address VARCHAR(64),
                        status VARCHAR(32),
                        request_json LONGTEXT,
                        result_json LONGTEXT,
                        raw_message LONGTEXT,
                        created_at DATETIME NULL,
                        finished_at DATETIME NULL,
                        received_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE KEY uk_agent_result_operation (operation_id),
                        KEY idx_agent_result_type (type),
                        KEY idx_agent_result_mac (mac_address)
                    )
                    """);

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS asset_export_logs (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        user_id BIGINT,
                        host_id BIGINT NOT NULL,
                        export_time DATETIME NOT NULL,
                        export_format VARCHAR(16) NOT NULL,
                        ip_address VARCHAR(64)
                    )
                    """);

            addColumnIfAbsent("accounts", "deleted", "TINYINT DEFAULT 0");
            addColumnIfAbsent("services", "deleted", "TINYINT DEFAULT 0");
            addColumnIfAbsent("processes", "deleted", "TINYINT DEFAULT 0");
            addColumnIfAbsent("apps", "deleted", "TINYINT DEFAULT 0");
            addColumnIfAbsent("accounts", "source", "VARCHAR(16) NOT NULL DEFAULT 'PLATFORM'");
            addColumnIfAbsent("services", "source", "VARCHAR(16) NOT NULL DEFAULT 'PLATFORM'");
            addColumnIfAbsent("processes", "source", "VARCHAR(16) NOT NULL DEFAULT 'PLATFORM'");
            addColumnIfAbsent("apps", "source", "VARCHAR(16) NOT NULL DEFAULT 'PLATFORM'");
            addColumnIfAbsent("port_scan_results", "tenant_id", "BIGINT NOT NULL DEFAULT 0");
            addColumnIfAbsent("port_scan_results", "deleted", "TINYINT DEFAULT 0");
            addColumnIfAbsent("port_scan_results", "source", "VARCHAR(16) NOT NULL DEFAULT 'PLATFORM'");
            addColumnIfAbsent("host_asset_inventory", "tenant_id", "BIGINT NOT NULL DEFAULT 0");
            addColumnIfAbsent("host_asset_inventory", "task_id", "VARCHAR(64) DEFAULT NULL");
            addColumnIfAbsent("host_asset_inventory", "deleted", "TINYINT DEFAULT 0");
            addColumnIfAbsent("host_asset_inventory", "source", "VARCHAR(16) NOT NULL DEFAULT 'PLATFORM'");
            addColumnIfAbsent("mq_error_logs", "tenant_id", "BIGINT NOT NULL DEFAULT 0");
            modifyColumnIfPossible("mq_error_logs", "error_reason", "LONGTEXT");
            modifyColumnIfPossible("accounts", "source", "VARCHAR(16) NOT NULL DEFAULT 'PLATFORM'");
            modifyColumnIfPossible("services", "source", "VARCHAR(16) NOT NULL DEFAULT 'PLATFORM'");
            modifyColumnIfPossible("processes", "source", "VARCHAR(16) NOT NULL DEFAULT 'PLATFORM'");
            modifyColumnIfPossible("apps", "source", "VARCHAR(16) NOT NULL DEFAULT 'PLATFORM'");
            modifyColumnIfPossible("port_scan_results", "source", "VARCHAR(16) NOT NULL DEFAULT 'PLATFORM'");
            modifyColumnIfPossible("host_asset_inventory", "source", "VARCHAR(16) NOT NULL DEFAULT 'PLATFORM'");
            addColumnIfAbsent("agent_result_operation", "tenant_id", "BIGINT NOT NULL DEFAULT 0");
            addColumnIfAbsent("agent_result_operation", "request_json", "LONGTEXT");
            addColumnIfAbsent("agent_result_operation", "result_json", "LONGTEXT");
            addColumnIfAbsent("agent_result_operation", "raw_message", "LONGTEXT");
            addColumnIfAbsent("agent_result_operation", "created_at", "DATETIME NULL");
            addColumnIfAbsent("agent_result_operation", "finished_at", "DATETIME NULL");
            addColumnIfAbsent("agent_result_operation", "received_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
            backfillSourceIfBlank("accounts");
            backfillSourceIfBlank("services");
            backfillSourceIfBlank("processes");
            backfillSourceIfBlank("apps");
            backfillSourceIfBlank("port_scan_results");
            backfillSourceIfBlank("host_asset_inventory");

            migrateLegacyPortRules();
            seedAssetFingerprintRules();
            dropLegacyPortRuleTable();

            insertPermission("asset:view", "查看资产管理", "/api/assets/**");
            insertPermission("asset:delete", "删除资产记录", "/api/assets/*/delete");
            insertPermission("asset:export", "导出资产清单", "/api/asset/export/**");
            insertPermission("asset-stats:view", "查看资产统计概览", "/api/asset-statistics/**");

            insertAssetMenus();
            grantAssetStatsPermission();
        };
    }

    private void addColumnIfAbsent(String table, String column, String definition) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        } catch (Exception ignored) {
        }
    }

    private void modifyColumnIfPossible(String table, String column, String definition) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " MODIFY COLUMN " + column + " " + definition);
        } catch (Exception ignored) {
        }
    }

    private void backfillSourceIfBlank(String table) {
        try {
            jdbcTemplate.update("UPDATE " + table + " SET source = 'PLATFORM' WHERE source IS NULL OR source = ''");
        } catch (Exception ignored) {
        }
    }

    private void insertPermission(String permissionCode, String permissionName, String path) {
        jdbcTemplate.update("""
                INSERT INTO sys_permission (permission_code, permission_name, permission_type, path, status)
                SELECT ?, ?, 'API', ?, 1
                WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = ?)
                """, permissionCode, permissionName, path, permissionCode);
    }

    private void insertAssetMenus() {
        Integer maxSort = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(sort_order), 0) FROM sys_menu", Integer.class);
        int baseSort = (maxSort == null ? 0 : maxSort) + 1;

        Integer assetViewPermId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_permission WHERE permission_code = 'asset:view'", Integer.class);

        jdbcTemplate.update("""
                UPDATE sys_menu SET menu_code = 'asset_parent', menu_name = '资产管理',
                menu_path = '#', menu_icon = 'layui-icon-component'
                WHERE menu_code = 'asset_overview' AND parent_id IS NULL
                """);

        jdbcTemplate.update("""
                INSERT INTO sys_menu (menu_code, menu_name, menu_path, menu_icon, permission_id, sort_order, status, parent_id)
                SELECT 'asset_parent', '资产管理', '#', 'layui-icon-component', ?, ?, 1, NULL
                WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'asset_parent')
                """, assetViewPermId, baseSort);

        Long parentId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE menu_code = 'asset_parent'", Long.class);

        jdbcTemplate.update("DELETE FROM sys_menu WHERE menu_code = 'asset_overview'");

        String[][] subMenus = {
                {"asset_account", "账号资产", "./pages/asset-account.html", "layui-icon-user"},
                {"asset_service", "服务资产", "./pages/asset-service.html", "layui-icon-service"},
                {"asset_process", "进程资产", "./pages/asset-process.html", "layui-icon-engine"},
                {"asset_app", "APP资产", "./pages/asset-app.html", "layui-icon-app"},
                {"asset_port", "端口资产", "./pages/asset-port.html", "layui-icon-release"},
                {"asset_stats", "资产统计概览", "./pages/asset-statistics.html", "layui-icon-chart-screen"}
        };

        for (int i = 0; i < subMenus.length; i++) {
            int sort = baseSort + 1 + i;
            Long permissionId = "asset_stats".equals(subMenus[i][0])
                    ? jdbcTemplate.queryForObject("SELECT id FROM sys_permission WHERE permission_code = 'asset-stats:view'", Long.class)
                    : assetViewPermId.longValue();
            jdbcTemplate.update("""
                    INSERT INTO sys_menu (menu_code, menu_name, menu_path, menu_icon, permission_id, sort_order, status, parent_id)
                    SELECT ?, ?, ?, ?, ?, ?, 1, ?
                    WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = ?)
                    """, subMenus[i][0], subMenus[i][1], subMenus[i][2], subMenus[i][3],
                    permissionId, sort, parentId, subMenus[i][0]);
        }

        String[] subMenuCodes = {"asset_account", "asset_service", "asset_process", "asset_app", "asset_port", "asset_stats"};
        for (String code : subMenuCodes) {
            jdbcTemplate.update(
                    "UPDATE sys_menu SET parent_id = ? WHERE menu_code = ? AND parent_id IS NULL",
                    parentId, code);
        }
    }

    private void grantAssetStatsPermission() {
        String[] roleCodes = {"SECURITY_ADMIN", "ANALYST", "AUDITOR", "TENANT_ADMIN"};
        for (String roleCode : roleCodes) {
            jdbcTemplate.update("""
                    INSERT INTO sys_role_permission (role_id, permission_id)
                    SELECT r.id, p.id
                    FROM sys_role r
                             JOIN sys_permission p ON p.permission_code = 'asset-stats:view'
                    WHERE r.role_code = ?
                      AND NOT EXISTS (
                        SELECT 1 FROM sys_role_permission rp
                        WHERE rp.role_id = r.id AND rp.permission_id = p.id
                    )
                    """, roleCode);
        }
    }

    private void migrateLegacyPortRules() {
        if (!tableExists("port_fingerprint_rule")) {
            return;
        }
        jdbcTemplate.query("""
                SELECT rule_name, match_type, port, service_pattern, banner_regex, product_name, product_type, confidence, priority
                FROM port_fingerprint_rule
                """, rs -> {
            String ruleName = rs.getString("rule_name");
            String matchType = rs.getString("match_type");
            Integer port = (Integer) rs.getObject("port");
            String servicePattern = rs.getString("service_pattern");
            String bannerRegex = rs.getString("banner_regex");
            String productName = rs.getString("product_name");
            String productType = rs.getString("product_type");
            Integer confidence = (Integer) rs.getObject("confidence");
            Integer priority = (Integer) rs.getObject("priority");

            String prefix = "pfl";
            String description = "[port_service] migrated";
            if ("BANNER_REGEX".equalsIgnoreCase(matchType)) {
                prefix = "pbr";
                description = "[banner_regex] migrated";
            } else if ("PORT_FALLBACK".equalsIgnoreCase(matchType)) {
                prefix = "pfb";
                description = "[port_fallback] migrated";
            }
            String ruleCode = prefix + "-" + normalizeRuleCode(ruleName);
            String category = normalizeCategory(productType);
            String subCategory = StringUtils.hasText(servicePattern) ? servicePattern : normalizeSubCategory(productName);
            insertAssetFingerprintRule(ruleCode, ruleName, category, subCategory, "tcp", port, bannerRegex, null,
                    productName, null, confidence == null ? 60 : confidence, description, 1, toAscendingPriority(priority));
        });
    }

    private void seedAssetFingerprintRules() {
        List<RuleSeed> rules = new ArrayList<>();

        addBannerRule(rules, "Apache HTTP Server", "middleware", "apache", 80,
                "(?i)server:\\s*apache(?:/(\\d+[\\w.\\-]*))?", "Apache Software Foundation", "Apache", 95, 10);
        addBannerRule(rules, "Nginx HTTP Server", "middleware", "nginx", 80,
                "(?i)server:\\s*nginx(?:/(\\d+[\\w.\\-]*))?", "NGINX", "Nginx", 95, 10);
        addBannerRule(rules, "Microsoft IIS", "middleware", "iis", 80,
                "(?i)server:\\s*microsoft-iis(?:/(\\d+[\\w.\\-]*))?", "Microsoft", "Microsoft IIS", 95, 10);
        addBannerRule(rules, "Tomcat Coyote", "middleware", "tomcat", 8080,
                "(?i)apache-coyote(?:/(\\d+[\\w.\\-]*))?", "Apache Software Foundation", "Apache Tomcat", 92, 12);
        addBannerRule(rules, "OpenSSH", "middleware", "ssh", 22,
                "(?i)openssh[_\\s/-]?([0-9][a-z0-9._-]*)", "OpenBSD", "OpenSSH", 96, 8);
        addBannerRule(rules, "MariaDB", "database", "mysql", 3306,
                "(?i)mariadb", "MariaDB", "MariaDB", 96, 8);
        addBannerRule(rules, "MySQL", "database", "mysql", 3306,
                "(?i)mysql_native_password|mysql", "Oracle", "MySQL", 94, 9);
        addBannerRule(rules, "VMware Authd 902", "middleware", "vmware", 902,
                "(?i)vmware authentication daemon", "VMware", "VMware Authentication Daemon", 96, 8);
        addBannerRule(rules, "VMware Authd 912", "middleware", "vmware", 912,
                "(?i)vmware authentication daemon", "VMware", "VMware Authentication Daemon", 96, 8);
        addBannerRule(rules, "HTTP Redirect Login", "webapp", "http", 8080,
                "(?i)location:\\s*https?://.*login", null, "Web Login Portal", 82, 25);
        addBannerRule(rules, "HTTP Response", "website", "http", 8888,
                "(?i)^http/1\\.[01]\\s", null, "HTTP Service", 78, 35);
        addBannerRule(rules, "HTTP Response 8082", "website", "http", 8082,
                "(?i)^http/1\\.[01]\\s", null, "HTTP Service", 78, 35);
        addBannerRule(rules, "Redis", "database", "redis", 6379,
                "(?i)redis|\\+pong", "Redis", "Redis", 93, 10);
        addBannerRule(rules, "RabbitMQ", "middleware", "amqp", 5672,
                "(?i)amqp", "VMware", "RabbitMQ", 85, 18);
        addBannerRule(rules, "RDP", "middleware", "rdp", 3389,
                "(?i)cookie: mstshash|rdp", "Microsoft", "Remote Desktop Services", 88, 18);

        String[][] serviceRules = {
                {"msrpc", "middleware", "msrpc", "Microsoft RPC Endpoint Mapper", "Microsoft", "135"},
                {"netbios-ssn", "middleware", "netbios", "NetBIOS Session Service", "Microsoft", "139"},
                {"microsoft-ds", "middleware", "smb", "Microsoft SMB", "Microsoft", "445"},
                {"mssql", "database", "mssql", "Microsoft SQL Server", "Microsoft", "1433"},
                {"mysql", "database", "mysql", "MySQL", "Oracle", "3306"},
                {"http-proxy", "webapp", "http", "Web Application", null, "8080"},
                {"http", "website", "http", "HTTP Service", null, "8888"},
                {"hive", "bigdata", "hive", "Apache Hive", "Apache", "10000"},
                {"postgresql", "database", "postgresql", "PostgreSQL", "PostgreSQL", "5432"},
                {"mongodb", "database", "mongodb", "MongoDB", "MongoDB", "27017"},
                {"redis", "database", "redis", "Redis", "Redis", "6379"},
                {"ssh", "middleware", "ssh", "OpenSSH", "OpenBSD", "22"},
                {"oracle", "database", "oracle", "Oracle Database", "Oracle", "1521"},
                {"amqp", "middleware", "amqp", "AMQP Service", null, "5672"},
                {"ldap", "middleware", "ldap", "LDAP Service", null, "389"},
                {"ldaps", "middleware", "ldap", "LDAPS Service", null, "636"},
                {"ftp", "middleware", "ftp", "FTP Service", null, "21"},
                {"smtp", "middleware", "smtp", "SMTP Service", null, "25"},
                {"imap", "middleware", "imap", "IMAP Service", null, "143"},
                {"pop3", "middleware", "pop3", "POP3 Service", null, "110"}
        };
        for (String[] item : serviceRules) {
            rules.add(new RuleSeed(
                    "pfl-" + item[0] + "-" + item[5],
                    item[3] + " Service Match",
                    item[1],
                    item[2],
                    "tcp",
                    Integer.parseInt(item[5]),
                    null,
                    item[4],
                    item[3],
                    null,
                    84,
                    "[port_service] generated",
                    1,
                    40
            ));
        }

        int[] fallbackPorts = {
                21, 22, 25, 53, 80, 88, 110, 111, 123, 135, 137, 138, 139, 143, 161, 389, 443, 445, 465, 514, 515,
                587, 631, 636, 873, 902, 912, 993, 995, 1025, 1080, 1433, 1434, 1521, 1883, 2049, 2181, 2375, 2379,
                2483, 2484, 3306, 3389, 4001, 4040, 4200, 4301, 4310, 4369, 4709, 4848, 5000, 5001, 5040, 5432, 5601,
                5672, 5900, 5984, 5985, 5986, 6379, 7001, 7002, 7077, 7180, 7474, 7680, 8000, 8001, 8008, 8009, 8025,
                8080, 8081, 8082, 8088, 8161, 8443, 8500, 8761, 8888, 9000, 9001, 9042, 9080, 9090, 9092, 9160, 9197,
                9200, 9210, 9300, 9418, 9996, 10000, 10050, 11211, 12441, 12451, 15672, 16450, 16453, 17001, 18080,
                19000, 23790, 25120, 27017, 28017, 28861, 32000, 33211, 36510, 37510, 38502, 42069, 49664, 49665, 49666,
                49667, 49668, 49674, 49675, 52322, 52623, 53277, 53278, 53292, 53295, 53325, 53327, 53372, 53391, 53437,
                53855, 53859, 54530, 58350, 58681, 59189, 62345, 63342, 63722, 64596, 64744, 64885, 65524
        };
        for (int port : fallbackPorts) {
            rules.add(new RuleSeed(
                    "pfb-port-" + port,
                    "Port " + port + " Fallback",
                    guessCategoryByPort(port),
                    guessSubCategoryByPort(port),
                    "tcp",
                    port,
                    null,
                    guessVendorByPort(port),
                    guessProductByPort(port),
                    null,
                    guessConfidenceByPort(port),
                    "[port_fallback] generated",
                    1,
                    90
            ));
        }

        for (RuleSeed rule : rules) {
            insertAssetFingerprintRule(rule.ruleCode(), rule.name(), rule.category(), rule.subCategory(), rule.protocol(),
                    rule.port(), rule.bannerRegex(), rule.vendor(), rule.product(), rule.versionExpr(), rule.confidence(),
                    rule.description(), rule.enabled(), rule.priority());
        }
    }

    private void addBannerRule(List<RuleSeed> rules, String name, String category, String subCategory, Integer port,
                               String bannerRegex, String vendor, String product, Integer confidence, Integer priority) {
        rules.add(new RuleSeed(
                "pbr-" + normalizeRuleCode(name),
                name + " Banner",
                category,
                subCategory,
                "tcp",
                port,
                bannerRegex,
                vendor,
                product,
                null,
                confidence,
                "[banner_regex] generated",
                1,
                priority
        ));
    }

    private void insertAssetFingerprintRule(String ruleCode, String name, String category, String subCategory,
                                            String protocol, Integer port, String bannerRegex, String vendor,
                                            String product, String versionExpr, Integer confidence, String description,
                                            Integer enabled, Integer priority) {
        jdbcTemplate.update("""
                INSERT INTO asset_fingerprint_rule
                (rule_code, name, category, sub_category, protocol, port, banner_regex, vendor, product, version_expr,
                 confidence, description, enabled, priority)
                SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                WHERE NOT EXISTS (SELECT 1 FROM asset_fingerprint_rule WHERE rule_code = ?)
                """,
                ruleCode, name, category, subCategory, protocol, port, bannerRegex, vendor, product, versionExpr,
                confidence, description, enabled, priority, ruleCode);
    }

    private void dropLegacyPortRuleTable() {
        try {
            jdbcTemplate.execute("DROP TABLE IF EXISTS port_fingerprint_rule");
        } catch (Exception ignored) {
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                """, Integer.class, tableName);
        return count != null && count > 0;
    }

    private String normalizeRuleCode(String value) {
        if (value == null) {
            return "unknown";
        }
        String normalized = value.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return normalized.isEmpty() ? "unknown" : normalized;
    }

    private int toAscendingPriority(Integer oldPriority) {
        int priority = oldPriority == null ? 50 : oldPriority;
        if (priority >= 90) {
            return 10;
        }
        if (priority >= 70) {
            return 40;
        }
        return 90;
    }

    private String normalizeCategory(String productType) {
        if (!StringUtils.hasText(productType)) {
            return "unknown";
        }
        String type = productType.trim().toLowerCase();
        return switch (type) {
            case "database", "db", "cache" -> "database";
            case "service", "middleware", "ssh", "smb", "msrpc" -> "middleware";
            case "bigdata", "hive", "kafka", "zookeeper", "hadoop" -> "bigdata";
            case "webapp", "website", "http", "https" -> "webapp";
            case "webframework", "framework" -> "webframework";
            default -> type;
        };
    }

    private String normalizeSubCategory(String productName) {
        if (!StringUtils.hasText(productName)) {
            return null;
        }
        return productName.trim().toLowerCase().replace(' ', '-');
    }

    private String guessCategoryByPort(int port) {
        if (port == 10000) {
            return "bigdata";
        }
        if (port == 80 || port == 443 || port == 8080 || port == 8081 || port == 8082 || port == 8088
                || port == 8443 || port == 8888 || port == 9197 || port == 9210) {
            return "webapp";
        }
        if (port == 3306 || port == 1433 || port == 1434 || port == 5432 || port == 6379 || port == 27017
                || port == 9042 || port == 9200 || port == 9300) {
            return "database";
        }
        return "middleware";
    }

    private String guessSubCategoryByPort(int port) {
        return switch (port) {
            case 135 -> "msrpc";
            case 139 -> "netbios";
            case 445 -> "smb";
            case 902, 912 -> "vmware";
            case 1433, 1434 -> "mssql";
            case 3306, 3336 -> "mysql";
            case 8080, 8081, 8082, 8088 -> "http";
            case 8888 -> "http";
            case 10000 -> "hive";
            case 7680 -> "delivery-optimization";
            default -> "port-" + port;
        };
    }

    private String guessVendorByPort(int port) {
        return switch (port) {
            case 135, 139, 445, 7680, 1433, 1434 -> "Microsoft";
            case 902, 912 -> "VMware";
            case 3306, 3336 -> "Oracle";
            case 10000 -> "Apache";
            default -> null;
        };
    }

    private String guessProductByPort(int port) {
        return switch (port) {
            case 135 -> "Microsoft RPC Endpoint Mapper";
            case 139 -> "NetBIOS Session Service";
            case 445 -> "Microsoft SMB";
            case 902, 912 -> "VMware Authentication Daemon";
            case 1433 -> "Microsoft SQL Server";
            case 1434 -> "Microsoft SQL Server Browser";
            case 3306 -> "MySQL";
            case 3336 -> "MySQL Compatible Service";
            case 7680 -> "Windows Delivery Optimization";
            case 8025 -> "HTTP Debug Service";
            case 8080, 8081, 8082, 8088, 8888 -> "HTTP Service";
            case 9197 -> "Web Console";
            case 9210 -> "Web Console";
            case 10000 -> "Apache Hive";
            default -> "TCP Port " + port + " Service";
        };
    }

    private int guessConfidenceByPort(int port) {
        return switch (port) {
            case 135, 139, 445, 902, 912, 1433, 1434, 3306, 7680, 10000 -> 72;
            case 8080, 8081, 8082, 8088, 8888 -> 48;
            default -> 30;
        };
    }

    private record RuleSeed(String ruleCode,
                            String name,
                            String category,
                            String subCategory,
                            String protocol,
                            Integer port,
                            String bannerRegex,
                            String vendor,
                            String product,
                            String versionExpr,
                            Integer confidence,
                            String description,
                            Integer enabled,
                            Integer priority) {
    }
}
