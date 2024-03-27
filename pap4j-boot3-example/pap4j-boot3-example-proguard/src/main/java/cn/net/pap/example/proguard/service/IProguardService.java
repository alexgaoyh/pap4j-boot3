package cn.net.pap.example.proguard.service;

import cn.net.pap.example.proguard.entity.Proguard;

import java.util.List;

public interface IProguardService {

    List<Proguard> searchAllByProguardName(String proguardName);

    Proguard saveAndFlush(Proguard entity);

}
