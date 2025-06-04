```sql

drop table if EXISTS "test";


CREATE TABLE "test" (
                        "id" numeric(18,0) NOT NULL,
                        "json1" "sys"."json",
                        CONSTRAINT "PRIMARY_qaz" PRIMARY KEY ("id")
);


insert into test(id, json1)
values (1, '[{"title": "张三", "creators": [{"role": "风", "creator": "111134"}, {"role": "火", "creator": "222245"}], "contributors": null}, {"title": "李四", "creators": [{"role": "雷", "creator": "222"}], "contributors": [{"role": "电", "contributor": "3333"}]}]');
insert into test(id, json1)
values (2, '[{"title": "张三", "creators": [{"role": "风", "creator": "111134"}, {"role": "火", "creator": "222245"}], "contributors": null}, {"title": "李四", "creators": [{"role": "雷", "creator": "222"}], "contributors": null}]');

-- select 
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


```