SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET character_set_client = utf8mb4;
SET character_set_connection = utf8mb4;
SET character_set_results = utf8mb4;

-- ---------------------------------------------------------------------
-- 奶茶饮品店 · 原料库存与配方出单
-- 库名 milktea_bar；docker 构建期实际建库使用 mysql/schema.sql 本文件内容一致。
-- mysql 容器是 UTC，时间戳一律写 UTC_TIMESTAMP() + INTERVAL 8 HOUR（北京时间）。

CREATE DATABASE IF NOT EXISTS milktea_bar
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE milktea_bar;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS recipe_topping;
DROP TABLE IF EXISTS cup_order_deduction;
DROP TABLE IF EXISTS cup_order_item;
DROP TABLE IF EXISTS cup_order;
DROP TABLE IF EXISTS recipe;
DROP TABLE IF EXISTS member;
DROP TABLE IF EXISTS ingredient;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------- 原料库存 ----------------
CREATE TABLE ingredient (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(64) NOT NULL UNIQUE COMMENT '原料名',
    category   VARCHAR(16) NOT NULL COMMENT '茶底 / 奶 / 小料 / 糖浆',
    stock      INT         NOT NULL COMMENT '库存',
    unit       VARCHAR(16) NOT NULL COMMENT '单位',
    status     VARCHAR(16) NOT NULL COMMENT '启用 / 停用',
    created_at DATETIME    COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 配方 ----------------
CREATE TABLE recipe (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(64) NOT NULL UNIQUE COMMENT '配方名',
    base_tea   VARCHAR(64) NOT NULL COMMENT '茶底',
    price      DECIMAL(10,2) NOT NULL COMMENT '售价',
    created_at DATETIME COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 配方小料用量：@ElementCollection + @CollectionTable + @MapKeyColumn 承载 Map<String,Integer>
CREATE TABLE recipe_topping (
    recipe_id     BIGINT NOT NULL,
    topping_name  VARCHAR(64) NOT NULL COMMENT '小料名（= ingredient.name）',
    qty           INT NOT NULL COMMENT '份数',
    PRIMARY KEY (recipe_id, topping_name),
    CONSTRAINT fk_topping_recipe FOREIGN KEY (recipe_id) REFERENCES recipe (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 会员 ----------------
CREATE TABLE member (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    phone      VARCHAR(24) NOT NULL UNIQUE COMMENT '手机号',
    name       VARCHAR(32) NOT NULL COMMENT '昵称',
    points     INT         NOT NULL DEFAULT 0 COMMENT '累计积分',
    level      VARCHAR(16) NOT NULL COMMENT '普通 / 银卡 / 金卡',
    created_at DATETIME    COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 出杯记录 ----------------
CREATE TABLE cup_order (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id      BIGINT     COMMENT '会员 id（散客为空）',
    pay_type       VARCHAR(16) NOT NULL COMMENT '微信 / 支付宝 / 现金',
    total          DECIMAL(10,2) NOT NULL COMMENT '金额合计（自动汇总）',
    status         VARCHAR(16) NOT NULL COMMENT '已出杯 / 已退款',
    points_earned  INT         COMMENT '本次为会员带来的积分（退款回退用）',
    stock_result   VARCHAR(16) NOT NULL DEFAULT '未扣减' COMMENT '原料扣减结果：已扣减 / 未扣减（历史旧单）',
    restore_result VARCHAR(16) COMMENT '退款原料回补结果：已回补 / 未回补（旧单无快照）；未退款为空',
    remark         VARCHAR(255) COMMENT '履约备注：如旧单无快照、原料失效等',
    created_at     DATETIME    COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE cup_order_item (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id   BIGINT NOT NULL COMMENT '所属出杯单',
    recipe_id  BIGINT NOT NULL COMMENT '配方 id',
    qty        INT    NOT NULL COMMENT '杯数（同一配方重复明细已合并）',
    CONSTRAINT fk_item_order FOREIGN KEY (order_id) REFERENCES cup_order (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 出杯原料扣减快照（履约台账） ----------------
-- 每笔出杯按「合并后的原料需求」落一行快照；退款严格按本快照回补，
-- 之后配方 / 原料如何编辑都不改变历史消耗与回补口径。
CREATE TABLE cup_order_deduction (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id        BIGINT      NOT NULL COMMENT '所属出杯单',
    ingredient_id   BIGINT      COMMENT '原料 id（回补锚点；原料若被物理删除则为空）',
    ingredient_name VARCHAR(64) NOT NULL COMMENT '原料名快照',
    category        VARCHAR(16) NOT NULL COMMENT '类别快照：茶底 / 奶 / 小料 / 糖浆',
    unit            VARCHAR(16) NOT NULL COMMENT '单位快照',
    qty             INT         NOT NULL COMMENT '本次实际扣减数量（跨配方合并后）',
    restored        TINYINT     NOT NULL DEFAULT 0 COMMENT '是否已随退款回补：1 是 / 0 否',
    created_at      DATETIME    COMMENT '创建时间',
    KEY idx_deduction_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- 种子数据
-- =====================================================================

INSERT IGNORE INTO ingredient (name, category, stock, unit, status, created_at) VALUES
('红茶',     '茶底', 50, 'g',  '启用', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
('绿茶',     '茶底', 40, 'g',  '启用', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
('乌龙茶',   '茶底', 30, 'g',  '启用', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
('鲜牛奶',   '奶',   60, 'ml', '启用', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
('珍珠',     '小料', 100, '份', '启用', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 55 DAY),
('椰果',     '小料', 80,  '份', '启用', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 55 DAY),
('红豆',     '小料', 70,  '份', '启用', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 55 DAY),
('布丁',     '小料', 40,  '份', '停用', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 50 DAY),
('黑糖糖浆', '糖浆', 90,  'ml', '启用', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 50 DAY),
('蜂蜜',     '糖浆', 35,  'ml', '停用', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 50 DAY);

INSERT IGNORE INTO recipe (name, base_tea, price, created_at) VALUES
('招牌珍珠奶茶', '红茶', 12.00, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 40 DAY),
('椰果绿茶',     '绿茶', 11.00, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 40 DAY),
('红豆乌龙',     '乌龙茶', 13.00, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 35 DAY),
('双倍珍珠奶茶', '红茶', 15.00, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 35 DAY),
('椰香红豆奶',   '绿茶', 12.00, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 30 DAY);

INSERT IGNORE INTO recipe_topping (recipe_id, topping_name, qty) VALUES
(1, '珍珠', 2), (1, '鲜牛奶', 1),
(2, '椰果', 2),
(3, '红豆', 2), (3, '黑糖糖浆', 1),
(4, '珍珠', 3), (4, '鲜牛奶', 1),
(5, '红豆', 1), (5, '椰果', 1);

INSERT IGNORE INTO member (phone, name, points, level, created_at) VALUES
('13800001111', '小明', 300,  '普通', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 20 DAY),
('13800002222', '小红', 600,  '银卡', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 20 DAY),
('13800003333', '阿强', 2200, '金卡', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 20 DAY),
('13800004444', '丽丽', 120,  '普通', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 18 DAY),
('13800005555', '老王', 1800, '银卡', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 18 DAY),
('13800006666', '婷婷', 50,   '普通', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 15 DAY);

INSERT IGNORE INTO cup_order (id, member_id, pay_type, total, status, points_earned, created_at) VALUES
(1, 1, '微信',   24.00, '已出杯', 24, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 10 DAY),
(2, 2, '支付宝', 11.00, '已出杯', 11, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 9 DAY),
(3, 3, '现金',   28.00, '已出杯', 28, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 8 DAY),
(4, 1, '微信',   36.00, '已退款', 36, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 7 DAY),
(5, NULL, '现金', 12.00, '已出杯', 0,  UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 6 DAY);

-- 历史旧单：功能上线前不存在扣减快照，按现有规则仍可退款，但不凭空回补库存。
UPDATE cup_order
SET stock_result = '未扣减',
    restore_result = CASE WHEN status = '已退款' THEN '未回补' ELSE restore_result END,
    remark = '历史旧单：无原料扣减快照，退款仅回退积分、不回补库存'
WHERE id IN (1, 2, 3, 4, 5);

INSERT IGNORE INTO cup_order_item (order_id, recipe_id, qty) VALUES
(1, 1, 2),
(2, 2, 1),
(3, 3, 1), (3, 4, 1),
(4, 5, 3),
(5, 1, 1);
