CREATE TABLE if not exists test.doris
(
    id TINYINT,
    doris_name CHAR(10) COMMENT "doris_name"
)
COMMENT "doris"
DISTRIBUTED BY HASH(id) BUCKETS 32
PROPERTIES (
    "replication_num" = "1"
);