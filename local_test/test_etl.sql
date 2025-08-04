drop table if exists user_login_sessions;

CREATE TABLE user_login_sessions
(
    session_id  INT PRIMARY KEY AUTO_INCREMENT,
    user_id     INT      NOT NULL,
    login_time  DATETIME NOT NULL,
    logout_time DATETIME,
    INDEX idx_user_id (user_id),
    INDEX idx_login_time (login_time)
);

INSERT INTO user_login_sessions (user_id, login_time, logout_time)
VALUES (1, '2023-01-01 08:00:00', '2023-01-01 18:00:00'),
       (1, '2023-01-02 09:15:00', '2023-01-02 17:30:00'),
       (1, '2023-01-03 08:30:00', '2023-01-03 19:00:00'),
       (1, '2023-01-04 10:00:00', '2023-01-04 16:45:00'),
       (1, '2023-01-05 08:20:00', '2023-01-05 17:50:00'),
       (1, '2023-01-08 09:00:00', '2023-01-08 18:00:00'),
       (1, '2023-01-09 08:45:00', '2023-01-09 17:30:00'),
       (1, '2023-01-12 10:30:00', '2023-01-12 15:00:00'),

       (2, '2023-01-01 08:30:00', '2023-01-01 17:45:00'),
       (2, '2023-01-02 09:00:00', '2023-01-02 18:30:00'),
       (2, '2023-01-03 10:15:00', '2023-01-03 16:00:00'),
       (2, '2023-01-05 08:45:00', '2023-01-05 17:00:00'),
       (2, '2023-01-06 09:30:00', '2023-01-06 18:15:00'),
       (2, '2023-01-07 08:00:00', '2023-01-07 19:00:00'),
       (3, '2023-01-01 10:00:00', '2023-01-01 16:00:00'),
       (3, '2023-01-03 09:30:00', '2023-01-03 17:45:00'),
       (3, '2023-01-05 08:15:00', '2023-01-05 18:30:00'),
       (3, '2023-01-07 10:45:00', '2023-01-07 15:15:00');

WITH login_dates AS (SELECT DISTINCT user_id,
                                     DATE(login_time) AS login_date
                     FROM user_login_sessions),

     date_groups AS (SELECT user_id,
                            login_date,
                            DATE_SUB(login_date, INTERVAL ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY login_date)
                                     DAY) AS date_group
                     FROM login_dates),

     consecutive_periods AS (SELECT user_id,
                                    date_group,
                                    COUNT(*)        AS consecutive_days,
                                    MIN(login_date) AS period_start,
                                    MAX(login_date) AS period_end
                             FROM date_groups
                             GROUP BY user_id, date_group),

     max_consecutive AS (SELECT user_id,
                                MAX(consecutive_days) AS max_consecutive_days
                         FROM consecutive_periods
                         GROUP BY user_id)

SELECT m.user_id,
       m.max_consecutive_days,
       CONCAT(c.period_start, ' 至 ', c.period_end) AS longest_period
FROM max_consecutive m
         JOIN consecutive_periods c ON m.user_id = c.user_id AND m.max_consecutive_days = c.consecutive_days
ORDER BY m.max_consecutive_days DESC;