package cn.net.pap.example.proguard.service.impl;

import cn.net.pap.example.proguard.entity.Proguard;
import cn.net.pap.example.proguard.repository.ProguardRepository;
import cn.net.pap.example.proguard.service.IProguardService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.hibernate.transform.AliasToEntityMapResultTransformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

@Service
public class ProguardServiceImpl implements IProguardService {

    @Autowired
    private ProguardRepository proguardRepository;

    @Autowired
    private EntityManager entityManager;

    @Override
    public List<Proguard> searchAllByProguardName(String proguardName) {
        List<Proguard> proguards = proguardRepository.searchAllByProguardName(proguardName);
        return proguards;
    }

    @Override
    public List<Proguard> findAll() {
        return proguardRepository.findAll();
    }

    @Override
    public Proguard saveAndFlush(Proguard entity) {
        Proguard proguard = proguardRepository.saveAndFlush(entity);
        return proguard;
    }

    @Override
    @Transactional
    public Boolean saveAndFlush2(Proguard... entitys) {
        for(Proguard proguard : entitys) {
            proguardRepository.saveAndFlush(proguard);
        }
        return true;
    }

    @Override
    public List<Proguard> saveAllAndFlush(List<Proguard> proguards) {
        return proguardRepository.saveAllAndFlush(proguards);
    }

    @Override
    public Proguard getProguardByProguardId(Long proguardId) {
        return proguardRepository.getProguardByProguardId(proguardId);
    }

    @Override
    public Proguard updateProguardByProguardId(Proguard proguard) {
        return proguardRepository.saveAndFlush(proguard);
    }

    @Override
    public List<Proguard> searchAllByProguardNameRange(String proguardNameRange) {
        // 解析范围字符串
        List<String> ranges = Arrays.asList(proguardNameRange.split(","));

        // 构建查询
        TypedQuery<Proguard> query = entityManager.createQuery(
                "SELECT e FROM Proguard e WHERE " +
                        "e.proguardName IN :ranges", Proguard.class);

        // 转换范围为整数列表
        List<Long> longRanges = ranges.stream()
                .flatMap(range -> {
                    if (range.contains("-")) {
                        String[] parts = range.split("-");
                        Long start = Long.parseLong(parts[0]);
                        Long end = Long.parseLong(parts[1]);
                        return LongStream.rangeClosed(start, end).boxed();
                    } else {
                        return Stream.of(Long.parseLong(range));
                    }
                })
                .collect(Collectors.toList());

        List<String> strRanges = longRanges.stream().map(String::valueOf).collect(Collectors.toList());

        // 设置参数并执行查询
        query.setParameter("ranges", strRanges);
        return query.getResultList();
    }

    @Override
    public Page<Proguard> searchAllByNaiveSQL(String naiveSQL, Pageable pageable) {
        Query naiveSQLQuery = entityManager.createNativeQuery(naiveSQL, Proguard.class);
        //设置分页
        naiveSQLQuery.setFirstResult((int)(pageable.getOffset()));
        naiveSQLQuery.setMaxResults(pageable.getPageSize());

        List resultList = naiveSQLQuery.getResultList();

        // 创建计数查询
        String countSQL = "SELECT COUNT(*) FROM (" + naiveSQL + ") AS countQuery";
        Query countQuery = entityManager.createNativeQuery(countSQL);
        // 获取总条数
        Number totalCount = (Number) countQuery.getSingleResult();

        return new PageImpl<>(resultList, pageable, totalCount.longValue());
    }

    @Override
    public Page<Map> searchAllByNaiveSQLMap(String naiveSQL, Pageable pageable) {
        NativeQuery naiveSQLQuery = (NativeQuery<?>) entityManager.createNativeQuery(naiveSQL);

        naiveSQLQuery.setResultTransformer(AliasToEntityMapResultTransformer.INSTANCE);

        //设置分页
        naiveSQLQuery.setFirstResult((int)(pageable.getOffset()));
        naiveSQLQuery.setMaxResults(pageable.getPageSize());

        List resultList = naiveSQLQuery.getResultList();

        // 创建计数查询
        String countSQL = "SELECT COUNT(*) FROM (" + naiveSQL + ") AS countQuery";
        Query countQuery = entityManager.createNativeQuery(countSQL);
        // 获取总条数
        Number totalCount = (Number) countQuery.getSingleResult();

        return new PageImpl<>(resultList, pageable, totalCount.longValue());
    }

    @Override
    @Transactional
    public Boolean executeNaiveSQLBatch(List<String> naiveSQLList, List<List<Object>> paramsList) {
        if(naiveSQLList != null && paramsList != null && naiveSQLList.size() == paramsList.size()) {
            for(int naiveIdx = 0; naiveIdx < naiveSQLList.size(); naiveIdx++) {
                String naiveSQL = naiveSQLList.get(naiveIdx);
                List<Object> params = paramsList.get(naiveIdx);
                Query query = entityManager.createNativeQuery(naiveSQL);
                for(int paramIdx = 0; paramIdx < params.size(); paramIdx++) {
                    query.setParameter(paramIdx + 1, params.get(paramIdx));
                }
                int i = query.executeUpdate();
                System.out.println(i);
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void deleteAllById(Long proguardId) {
        proguardRepository.deleteById(proguardId);
    }

    @Override
    @Transactional
    public Boolean executeNaiveSQLInsertBatchUsingJDBC(List<String> paramsList) {
        try {
            Session session = entityManager.unwrap(Session.class);
            return session.doReturningWork(connection -> {
                // todo 此处未处理 NULL 字段
                try (PreparedStatement preparedStatement = connection.prepareStatement(
                        "INSERT INTO proguard (proguard_id, tenant_id) VALUES (?, 1)")) {

                    connection.setAutoCommit(false);

                    for (int idx = 0; idx < paramsList.size(); idx++) {
                        preparedStatement.setObject(1, paramsList.get(idx));
                        preparedStatement.addBatch();
                    }
                    int[] result = preparedStatement.executeBatch();
                    connection.commit();

                } catch (SQLException e) {
                    try {
                        connection.rollback();
                    } catch (SQLException rollbackEx) {
                        rollbackEx.printStackTrace();
                    }
                    throw new RuntimeException("Batch insert failed", e);
                }

                return true;
            });
        } catch (Exception e) {
            throw new RuntimeException("Error during batch insert", e);
        }
    }

    @Override
    @Transactional
    public Boolean executeNaiveSQLBatchUsingJDBC(List<String> executeSQLList) {
        try {
            Session session = entityManager.unwrap(Session.class);
            return session.doReturningWork(connection -> {
                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement()) {
                    for (String executeSQL : executeSQLList) {
                        statement.addBatch(executeSQL);
                    }

                    int[] results = statement.executeBatch();

                    connection.commit();
                    return true;

                } catch (SQLException e) {
                    try {
                        connection.rollback();
                    } catch (SQLException rollbackEx) {
                        rollbackEx.printStackTrace();
                    }
                    throw new RuntimeException("Batch execution failed", e);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Error during batch execution", e);
        }
    }

}
