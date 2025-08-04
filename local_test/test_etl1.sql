-- 8. 清理临时表（可选）
DROP TABLE IF EXISTS source_sales;

-- 1. 创建源数据表（模拟原始数据）
CREATE TABLE IF NOT EXISTS source_sales
(
    id           INT AUTO_INCREMENT PRIMARY KEY,
    order_id     VARCHAR(50),
    customer_id  INT,
    product_name VARCHAR(100),
    quantity     INT,
    unit_price   DECIMAL(10, 2),
    order_date   VARCHAR(20), -- 原始格式可能不规范
    region       VARCHAR(50),
    discount     VARCHAR(10), -- 可能是百分比或固定值
    sales_rep    VARCHAR(100)
);

-- 2. 插入源数据
INSERT INTO source_sales (order_id, customer_id, product_name, quantity, unit_price, order_date, region, discount,
                          sales_rep)
VALUES ('ORD1001', 101, 'Laptop', 1, 999.99, '2023-05-15', 'North', '10%', 'John Smith'),
       ('ORD1002', 102, 'Mouse', 2, 25.50, '15/05/2023', 'South', '5', 'Jane Doe'),
       ('ORD1003', 103, 'Keyboard', 1, 49.99, '2023-05-16', 'East', '0%', 'John Smith'),
       ('ORD1004', 101, 'Monitor', 1, 199.99, '16/05/2023', 'North', '15%', 'Jane Doe'),
       ('ORD1005', 104, 'Headphones', 3, 79.99, '2023-05-17', 'West', '8', 'Mike Johnson'),
       ('ORD1006', 105, 'USB Cable', 5, 9.99, '17/05/2023', 'South', '0', 'Jane Doe'),
       ('ORD1007', 102, 'Laptop', 1, 899.99, '2023-05-18', 'East', '12%', 'John Smith'),
       ('ORD1008', 106, 'Mouse Pad', 1, 12.50, '2023-05-18', 'North', '0%', 'Mike Johnson');

-- 3. 创建目标表（数据仓库表）
CREATE TABLE IF NOT EXISTS dw_sales_fact
(
    sales_id        INT AUTO_INCREMENT PRIMARY KEY,
    order_id        VARCHAR(50),
    customer_id     INT,
    product_id      INT,
    quantity        INT,
    unit_price      DECIMAL(10, 2),
    total_price     DECIMAL(12, 2),
    discount_amount DECIMAL(10, 2),
    final_price     DECIMAL(12, 2),
    order_date      DATE,
    region_id       INT,
    sales_rep_id    INT,
    etl_timestamp   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dw_products
(
    product_id    INT AUTO_INCREMENT PRIMARY KEY,
    product_name  VARCHAR(100) UNIQUE,
    category      VARCHAR(50),
    etl_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dw_regions
(
    region_id     INT AUTO_INCREMENT PRIMARY KEY,
    region_name   VARCHAR(50) UNIQUE,
    etl_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dw_sales_reps
(
    sales_rep_id   INT AUTO_INCREMENT PRIMARY KEY,
    sales_rep_name VARCHAR(100) UNIQUE,
    etl_timestamp  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. ETL处理过程

-- 4.1 首先处理维度表
-- 处理产品维度
INSERT IGNORE INTO dw_products (product_name, category)
SELECT DISTINCT product_name,
                CASE
                    WHEN product_name IN ('Laptop', 'Monitor') THEN 'Computers'
                    WHEN product_name IN ('Mouse', 'Keyboard', 'Mouse Pad') THEN 'Accessories'
                    ELSE 'Other'
                    END AS category
FROM source_sales;

-- 处理区域维度
INSERT IGNORE INTO dw_regions (region_name)
SELECT DISTINCT region
FROM source_sales;

-- 处理销售代表维度
INSERT IGNORE INTO dw_sales_reps (sales_rep_name)
SELECT DISTINCT sales_rep
FROM source_sales;

-- 4.2 处理事实表
INSERT INTO dw_sales_fact (order_id,
                           customer_id,
                           product_id,
                           quantity,
                           unit_price,
                           total_price,
                           discount_amount,
                           final_price,
                           order_date,
                           region_id,
                           sales_rep_id)
SELECT s.order_id,
       s.customer_id,
       p.product_id,
       s.quantity,
       s.unit_price,
       s.quantity * s.unit_price AS total_price,
       CASE
           WHEN s.discount LIKE '%\%' THEN
               (s.quantity * s.unit_price) * CAST(REPLACE(s.discount, '%', '') AS DECIMAL(5, 2)) / 100
           ELSE (s.quantity * s.unit_price) * CAST(s.discount AS DECIMAL(5, 2)) / 100
           END                   AS discount_amount,
       (s.quantity * s.unit_price) -
       CASE
           WHEN s.discount LIKE '%\%' THEN
               (s.quantity * s.unit_price) * CAST(REPLACE(s.discount, '%', '') AS DECIMAL(5, 2)) / 100
           ELSE (s.quantity * s.unit_price) * CAST(s.discount AS DECIMAL(5, 2)) / 100
           END                   AS final_price,
       CASE
           WHEN s.order_date LIKE '%/%' THEN STR_TO_DATE(s.order_date, '%d/%m/%Y')
           ELSE STR_TO_DATE(s.order_date, '%Y-%m-%d')
           END                   AS order_date,
       r.region_id,
       sr.sales_rep_id
FROM source_sales s
         JOIN
     dw_products p ON s.product_name = p.product_name
         JOIN
     dw_regions r ON s.region = r.region_name
         JOIN
     dw_sales_reps sr ON s.sales_rep = sr.sales_rep_name;

-- 5. 创建物化视图/汇总表（可选）
CREATE TABLE IF NOT EXISTS sales_summary
(
    summary_date     DATE,
    region_name      VARCHAR(50),
    product_category VARCHAR(50),
    total_sales      DECIMAL(12, 2),
    total_quantity   INT,
    avg_discount     DECIMAL(5, 2),
    PRIMARY KEY (summary_date, region_name, product_category)
);

-- 6. 更新汇总表
INSERT INTO sales_summary (summary_date,
                           region_name,
                           product_category,
                           total_sales,
                           total_quantity,
                           avg_discount)
SELECT DATE_FORMAT(ds.order_date, '%Y-%m-01')           AS summary_date,
       dr.region_name,
       dp.category                                      AS product_category,
       SUM(ds.final_price)                              AS total_sales,
       SUM(ds.quantity)                                 AS total_quantity,
       AVG((ds.discount_amount / ds.total_price) * 100) AS avg_discount
FROM dw_sales_fact ds
         JOIN
     dw_regions dr ON ds.region_id = dr.region_id
         JOIN
     dw_products dp ON ds.product_id = dp.product_id
GROUP BY DATE_FORMAT(ds.order_date, '%Y-%m-01'),
         dr.region_name,
         dp.category
ON DUPLICATE KEY UPDATE total_sales    = VALUES(total_sales),
                        total_quantity = VALUES(total_quantity),
                        avg_discount   = VALUES(avg_discount);

-- 7. 示例查询

-- 7.1 查看ETL后的销售事实数据
SELECT ds.order_id,
       ds.customer_id,
       dp.product_name,
       ds.quantity,
       ds.unit_price,
       ds.total_price,
       ds.discount_amount,
       ds.final_price,
       ds.order_date,
       dr.region_name,
       dsr.sales_rep_name
FROM dw_sales_fact ds
         JOIN
     dw_products dp ON ds.product_id = dp.product_id
         JOIN
     dw_regions dr ON ds.region_id = dr.region_id
         JOIN
     dw_sales_reps dsr ON ds.sales_rep_id = dsr.sales_rep_id
ORDER BY ds.order_date DESC
LIMIT 5;
