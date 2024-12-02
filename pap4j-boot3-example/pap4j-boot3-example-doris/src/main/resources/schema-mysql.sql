drop table if exists doris;

CREATE TABLE if not exists doris
(
    id INT,
    doris_name CHAR(20) COMMENT "doris_name",
    doris_remark CHAR(20) COMMENT "doris_name"
)
UNIQUE KEY(`id`)
COMMENT "doris"
DISTRIBUTED BY HASH(id) BUCKETS 32
PROPERTIES (
   "replication_num" = "1",
   "enable_unique_key_merge_on_write" = "true"
);