package cn.net.pap.example.proguard.service;

import cn.net.pap.example.proguard.entity.AutoIncrePreKey;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface IAutoIncrePreKeyService {

    AutoIncrePreKey saveAndFlush(AutoIncrePreKey entity);

    List<AutoIncrePreKey> saveAndFlushBatch(List<AutoIncrePreKey> list);

    AutoIncrePreKey saveAndFlushThrowRuntimeException(AutoIncrePreKey entity) throws RuntimeException;

    AutoIncrePreKey saveAndFlushThrowIOException(AutoIncrePreKey entity) throws IOException;

    List<AutoIncrePreKey> findAll();

    Map<String, List<AutoIncrePreKey>> batch(List<AutoIncrePreKey> autoIncrePreKeyList);

}
