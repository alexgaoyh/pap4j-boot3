```sql

drop table if EXISTS "test";


CREATE TABLE "test" (
                        "id" numeric(18,0) NOT NULL,
                        "json1" "sys"."json",
                        CONSTRAINT "PRIMARY_qaz" PRIMARY KEY ("id")
);


insert into test(id, json1)
values (1, '[{"title": "张三", "array": ["1", "2", "3"], "creators": [{"role": "风", "creator": "111134"}, {"role": "火", "creator": "222245"}], "contributors": null}, {"title": "李四",  "array": ["4", "5", "6"], "creators": [{"role": "雷", "creator": "222"}], "contributors": [{"role": "电", "contributor": "3333"}]}]');
insert into test(id, json1)
values (2, '[{"title": "张三", "array": ["a", "b", "c"], "creators": [{"role": "风", "creator": "111134"}, {"role": "火", "creator": "222245"}], "contributors": null}, {"title": "李四",  "array": ["d", "e", "f"], "creators": [{"role": "雷", "creator": "222"}], "contributors": null}]');
insert into test(id, json1)
values (3, '[{"title": "张三", "array": ["11", "12", "13"], "creators": [{"role": "风", "creator": "111134"}, {"role": "火", "creator": "222245"}], "contributors": null}, {"title": "王五",  "array": ["14", "15", "16"], "creators": [{"role": "雷", "creator": "222"}], "contributors": null}]');

-- 数组内自定义对象的过滤 兼容 null  
SELECT
    *
FROM
    test,
    json_array_elements(json1) AS json1_elem,
    json_array_elements(json1_elem->'creators') AS json1_elem_creator_elem
WHERE
    json1_elem->>'title' LIKE '%李%'
    AND json1_elem_creator_elem->>'role' LIKE '%雷%'
    AND ( json1_elem->'contributors' IS NOT NULL and jsonb_typeof(json1_elem->'contributors') = 'array' AND EXISTS (
        SELECT 1
        FROM json_array_elements(json1_elem->'contributors') AS elem
        WHERE elem->>'role' = '电') 
    );

-- 数组内自定义对象的过滤 兼容 null
SELECT
    *
FROM
    test,
    json_array_elements(json1) AS json1_elem,
    json_array_elements(json1_elem->'creators') AS json1_elem_creator_elem
WHERE
    json1_elem->>'title' LIKE '%李%'
    AND json1_elem_creator_elem->>'role' LIKE '%雷%'
    AND ( NOT EXISTS (SELECT 1
        FROM json_array_elements(test.json1) AS elem
        WHERE jsonb_typeof(elem->'contributors') != 'null')
    ) ;

-- 数组内字符串的过滤 string_agg
SELECT *,
       ( SELECT string_agg ( elem ->> 'title', '^^^' ) FROM json_array_elements ( json1 ) AS elem ) AS titles_combined
FROM
    test
where ( SELECT string_agg ( elem ->> 'title', '^^^' ) FROM json_array_elements ( json1 ) AS elem )  like '%王%'

-- 数组内数组的过滤 jsonb
SELECT
    *
FROM
    test,
    jsonb_array_elements ( json1 :: jsonb ) AS elem
WHERE
    elem -> 'array' @> '["12"]' :: jsonb;
```