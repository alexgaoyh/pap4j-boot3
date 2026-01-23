package cn.net.pap.example.proguard.service;

import cn.net.pap.example.proguard.entity.AutoIncrePreKey;

import java.io.IOException;
import java.util.List;

public interface IAutoIncrePreKeyService {

    AutoIncrePreKey saveAndFlush(AutoIncrePreKey entity);

    AutoIncrePreKey saveAndFlushThrowRuntimeException(AutoIncrePreKey entity) throws RuntimeException;

    AutoIncrePreKey saveAndFlushThrowIOException(AutoIncrePreKey entity) throws IOException;

    List<AutoIncrePreKey> findAll();
}
