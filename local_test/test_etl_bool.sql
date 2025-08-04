CREATE TABLE test_table_bool
(
    id   VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128),
    flag INT
);
INSERT INTO test_table_bool
VALUES ('1', '张三', 1);
INSERT INTO test_table_bool
VALUES ('2', '李四', 0);
INSERT INTO test_table_bool
VALUES ('3', '王五', 1);