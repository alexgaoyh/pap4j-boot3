package cn.net.pap.example.proguard.service;

import cn.net.pap.example.proguard.entity.Proguard;

import java.util.List;

public interface IProguardService {

    List<Proguard> searchAllByProguardName(String proguardName);

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
}
