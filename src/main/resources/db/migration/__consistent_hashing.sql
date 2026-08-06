CREATE TABLE shard_node (
    node_id   int PRIMARY KEY,
    dsn       text NOT NULL           -- строка подключения / имя шарда
);

-- точки на кольце (виртуальные узлы)
CREATE TABLE hash_ring (
    ring_pos  bigint PRIMARY KEY,     -- позиция на кольце [0, 2^32)
    node_id   int NOT NULL REFERENCES shard_node(node_id)
);
CREATE INDEX ON hash_ring (ring_pos);

CREATE OR REPLACE FUNCTION ring_hash(key text)
RETURNS bigint LANGUAGE sql IMMUTABLE AS $$
    -- hashtextextended -> int8, приводим к [0, 2^32)
    SELECT (hashtextextended(key, 0) & x'00000000FFFFFFFF'::bigint);
$$;

INSERT INTO hash_ring (ring_pos, node_id)
SELECT ring_hash(n.node_id || '#' || g.i), n.node_id
FROM shard_node n
CROSS JOIN generate_series(0, 127) AS g(i)
ON CONFLICT DO NOTHING;

CREATE OR REPLACE FUNCTION shard_for(key text)
RETURNS int LANGUAGE sql STABLE AS $$
    SELECT node_id FROM (
        SELECT node_id FROM hash_ring
        WHERE ring_pos >= ring_hash(key)
        ORDER BY ring_pos LIMIT 1
    ) s
    UNION ALL
    SELECT node_id FROM hash_ring ORDER BY ring_pos LIMIT 1
    LIMIT 1;
$$;