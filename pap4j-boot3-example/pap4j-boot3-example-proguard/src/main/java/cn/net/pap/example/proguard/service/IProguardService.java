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
}
