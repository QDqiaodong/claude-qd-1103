-- H2（MySQL 兼容模式）测试建表 + 种子数据，结构与生产 schema.sql 保持一致。
SET MODE MySQL;

DROP TABLE IF EXISTS recipe_topping;
DROP TABLE IF EXISTS cup_order_deduction;
DROP TABLE IF EXISTS cup_order_item;
DROP TABLE IF EXISTS cup_order;
DROP TABLE IF EXISTS recipe;
DROP TABLE IF EXISTS member;
DROP TABLE IF EXISTS ingredient;

CREATE TABLE ingredient (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(64) NOT NULL UNIQUE,
    category   VARCHAR(16) NOT NULL,
    stock      INT         NOT NULL,
    unit       VARCHAR(16) NOT NULL,
    status     VARCHAR(16) NOT NULL,
    created_at DATETIME
);

CREATE TABLE recipe (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(64) NOT NULL UNIQUE,
    base_tea   VARCHAR(64) NOT NULL,
    price      DECIMAL(10,2) NOT NULL,
    created_at DATETIME
);

CREATE TABLE recipe_topping (
    recipe_id     BIGINT NOT NULL,
    topping_name  VARCHAR(64) NOT NULL,
    qty           INT NOT NULL,
    PRIMARY KEY (recipe_id, topping_name),
    CONSTRAINT fk_topping_recipe FOREIGN KEY (recipe_id) REFERENCES recipe (id)
);

CREATE TABLE member (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    phone      VARCHAR(24) NOT NULL UNIQUE,
    name       VARCHAR(32) NOT NULL,
    points     INT         NOT NULL DEFAULT 0,
    level      VARCHAR(16) NOT NULL,
    created_at DATETIME
);

CREATE TABLE cup_order (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id      BIGINT,
    pay_type       VARCHAR(16) NOT NULL,
    total          DECIMAL(10,2) NOT NULL,
    status         VARCHAR(16) NOT NULL,
    points_earned  INT,
    stock_result   VARCHAR(16) NOT NULL DEFAULT '未扣减',
    restore_result VARCHAR(16),
    remark         VARCHAR(255),
    created_at     DATETIME
);

CREATE TABLE cup_order_item (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id   BIGINT NOT NULL,
    recipe_id  BIGINT NOT NULL,
    qty        INT    NOT NULL,
    CONSTRAINT fk_item_order FOREIGN KEY (order_id) REFERENCES cup_order (id)
);

CREATE TABLE cup_order_deduction (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id        BIGINT      NOT NULL,
    ingredient_id   BIGINT,
    ingredient_name VARCHAR(64) NOT NULL,
    category        VARCHAR(16) NOT NULL,
    unit            VARCHAR(16) NOT NULL,
    qty             INT         NOT NULL,
    restored        TINYINT     NOT NULL DEFAULT 0,
    created_at      DATETIME
);
CREATE INDEX idx_deduction_order ON cup_order_deduction (order_id);

INSERT INTO ingredient (name, category, stock, unit, status, created_at) VALUES
('红茶',     '茶底', 50, 'g',  '启用', TIMESTAMP '2026-08-01 10:00:00'),
('绿茶',     '茶底', 40, 'g',  '启用', TIMESTAMP '2026-08-01 10:00:00'),
('乌龙茶',   '茶底', 30, 'g',  '启用', TIMESTAMP '2026-08-01 10:00:00'),
('鲜牛奶',   '奶',   60, 'ml', '启用', TIMESTAMP '2026-08-01 10:00:00'),
('珍珠',     '小料', 100, '份', '启用', TIMESTAMP '2026-08-01 10:00:00'),
('椰果',     '小料', 80,  '份', '启用', TIMESTAMP '2026-08-01 10:00:00'),
('红豆',     '小料', 70,  '份', '启用', TIMESTAMP '2026-08-01 10:00:00'),
('布丁',     '小料', 40,  '份', '停用', TIMESTAMP '2026-08-01 10:00:00'),
('黑糖糖浆', '糖浆', 90,  'ml', '启用', TIMESTAMP '2026-08-01 10:00:00'),
('蜂蜜',     '糖浆', 35,  'ml', '停用', TIMESTAMP '2026-08-01 10:00:00');

INSERT INTO recipe (name, base_tea, price, created_at) VALUES
('招牌珍珠奶茶', '红茶', 12.00, TIMESTAMP '2026-09-01 10:00:00'),
('椰果绿茶',     '绿茶', 11.00, TIMESTAMP '2026-09-01 10:00:00'),
('红豆乌龙',     '乌龙茶', 13.00, TIMESTAMP '2026-09-01 10:00:00'),
('双倍珍珠奶茶', '红茶', 15.00, TIMESTAMP '2026-09-01 10:00:00'),
('椰香红豆奶',   '绿茶', 12.00, TIMESTAMP '2026-09-01 10:00:00');

INSERT INTO recipe_topping (recipe_id, topping_name, qty) VALUES
(1, '珍珠', 2), (1, '鲜牛奶', 1),
(2, '椰果', 2),
(3, '红豆', 2), (3, '黑糖糖浆', 1),
(4, '珍珠', 3), (4, '鲜牛奶', 1),
(5, '红豆', 1), (5, '椰果', 1);

INSERT INTO member (phone, name, points, level, created_at) VALUES
('13800001111', '小明', 300,  '普通', TIMESTAMP '2026-09-10 10:00:00'),
('13800002222', '小红', 600,  '银卡', TIMESTAMP '2026-09-10 10:00:00');

-- 历史旧单：无扣减快照
INSERT INTO cup_order (id, member_id, pay_type, total, status, points_earned,
                       stock_result, restore_result, remark, created_at) VALUES
(1, 1, '微信', 24.00, '已出杯', 24, '未扣减', NULL,
 '历史旧单：无原料扣减快照，退款仅回退积分、不回补库存', TIMESTAMP '2026-09-20 10:00:00');
INSERT INTO cup_order_item (order_id, recipe_id, qty) VALUES (1, 1, 2);
