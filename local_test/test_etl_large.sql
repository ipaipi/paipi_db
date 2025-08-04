CREATE TABLE test_table_large
(
    id          VARCHAR(64) PRIMARY KEY,
    name        VARCHAR(128),
    age         INT,
    email       VARCHAR(128),
    create_time VARCHAR(32)
);
INSERT INTO test_table_large
VALUES ('1', '张三', 18, 'zhangsan@test.com', '2024-01-01 10:00:00');
INSERT INTO test_table_large
VALUES ('2', '李四', 22, 'li4@test.com', '2024-01-02 11:00:00');
INSERT INTO test_table_large
VALUES ('3', '王五', 30, 'wangwu@test.com', '2024-01-03 12:00:00');
INSERT INTO test_table_large
VALUES ('4', '赵六', 25, 'zhaoliu@test.com', '2024-01-04 13:00:00');
INSERT INTO test_table_large
VALUES ('5', '孙七', 28, 'sunqi@test.com', '2024-01-05 14:00:00');