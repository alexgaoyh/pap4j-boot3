package cn.net.pap.example.proguard.service.impl;

import cn.net.pap.example.proguard.callback.TempQueryCallback;
import cn.net.pap.example.proguard.entity.TempQuery;
import cn.net.pap.example.proguard.repository.TempQueryRepository;
import cn.net.pap.example.proguard.service.ITempQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
public class TempQueryServiceImpl implements ITempQueryService {

    @Autowired
    private TempQueryRepository tempQueryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Transactional
    public void batchInsert(String bizType, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        int batchSize = 500;
        List<Long> buffer = new ArrayList<>(batchSize);
        for (Long id : ids) {
            buffer.add(id);
            if (buffer.size() == batchSize) {
                doBatchInsert(bizType, buffer);
                buffer.clear();
            }
        }
        if (!buffer.isEmpty()) {
            doBatchInsert(bizType, buffer);
        }
    }

    private void doBatchInsert(String bizType, List<Long> batch) {
        String sql = "INSERT INTO temp_query (biz_type, id) VALUES (?, ?)";
        jdbcTemplate.batchUpdate(
                sql,
                batch,
                batch.size(),
                (ps, id) -> {
                    ps.setString(1, bizType);
                    ps.setLong(2, id);
                }
        );
    }

    private void doInsert(String bizType, List<Long> ids) {
        for (Long id : ids) {
            tempQueryRepository.save(new TempQuery(bizType, id));
        }
    }

    @Override
    public List<TempQuery> listByBizType(String bizType) {
        return tempQueryRepository.findByBizType(bizType);
    }

    @Override
    public List<TempQuery> listByBizTypeAndIds(String bizType, Collection<Long> ids) {
        return tempQueryRepository.findByBizTypeAndIdIn(bizType, ids);
    }

    @Override
    public long countByBizType(String bizType) {
        return tempQueryRepository.countByBizType(bizType);
    }

    @Override
    public boolean exists(String bizType, Long id) {
        return tempQueryRepository.existsByBizTypeAndId(bizType, id);
    }

    @Override
    @Transactional
    public int deleteByBizType(String bizType) {
        return tempQueryRepository.deleteByBizType(bizType);
    }

    @Override
    public List<TempQuery> listByIdRange(Long startId, Long endId) {
        return tempQueryRepository.findByIdRange(startId, endId);
    }

    @Override
    public void save(TempQuery tempQuery) {
        tempQueryRepository.save(tempQuery);
    }

    @Override
    @Transactional
    public <T> T executeWithTempQuery(String bizType, Collection<Long> ids, TempQueryCallback<T> callback) {
        try {
            batchInsert(bizType, ids);
            return callback.execute();
        } finally {
            deleteByBizType(bizType);
        }
    }

}