package cn.net.pap.example.proguard.service;

import cn.net.pap.example.proguard.entity.Proguard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public interface IProguardService {

    List<Proguard> searchAllByProguardName(String proguardName);

    List<Proguard> findAll();

    Proguard saveAndFlush(Proguard entity);

    /**
     * 批量保存，返回值含主键
     * @param proguards
     * @return
     */
    List<Proguard> saveAllAndFlush(List<Proguard> proguards);

    Proguard getProguardByProguardId(Long proguardId);

    Proguard updateProguardByProguardId(Proguard proguard);

    /**
     * 范围查询 参数格式为：  A-D,H   代表从 A 到 D，并且包含 H 的 数据   in (A,B,C,D,H)
     * @param proguardNameRange
     * @return
     */
    List<Proguard> searchAllByProguardNameRange(String proguardNameRange);

    /**
     * 原生SQL 分页查询
     * @param naiveSQL
     * @param pageable
     * @return
     */
    Page<Proguard> searchAllByNaiveSQL(String naiveSQL, Pageable pageable);

    /**
     * 原生SQL 分页查询 返回 Map
     * @param naiveSQL
     * @param pageable
     * @return
     */
    Page<Map> searchAllByNaiveSQLMap(String naiveSQL, Pageable pageable);

    /**
     * 批量执行多个SQL
     * 实现类增加 org.springframework.transaction.annotation.Transactional 注解
     * @param naiveSQLList
     * @param paramsList
     * @return
     */
    Boolean executeNaiveSQLBatch(List<String> naiveSQLList, List<List<Object>> paramsList);

    void deleteAllById(Long proguardId);

    /**
     * 批量执行多个SQL
     * 实现类增加 org.springframework.transaction.annotation.Transactional 注解
     * @param paramsList
     * @return
     */
    Boolean executeNaiveSQLInsertBatchUsingJDBC(List<String> paramsList);

    /**
     * 批量执行多个SQL
     * 实现类增加 org.springframework.transaction.annotation.Transactional 注解
     * @param executeSQLList
     * @return
     */
    Boolean executeNaiveSQLBatchUsingJDBC(List<String> executeSQLList);

}
