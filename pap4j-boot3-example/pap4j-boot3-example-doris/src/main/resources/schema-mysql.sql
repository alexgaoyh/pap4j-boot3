drop table if exists doris;

CREATE TABLE if not exists doris
(
    id INT,
    doris_name CHAR(20) COMMENT "doris_name",
    doris_remark CHAR(20) COMMENT "doris_name"
)
COMMENT "doris"
DISTRIBUTED BY HASH(id) BUCKETS 32
PROPERTIES (
    "replication_num" = "1"
);