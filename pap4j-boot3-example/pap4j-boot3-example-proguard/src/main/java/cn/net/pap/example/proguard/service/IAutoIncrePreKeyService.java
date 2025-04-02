package cn.net.pap.example.proguard.service;

import cn.net.pap.example.proguard.entity.AutoIncrePreKey;

import java.util.List;

public interface IAutoIncrePreKeyService {

    AutoIncrePreKey saveAndFlush(AutoIncrePreKey entity);

    AutoIncrePreKey saveAndFlushThrowException(AutoIncrePreKey entity);

    List<AutoIncrePreKey> findAll();
}
