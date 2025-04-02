package cn.net.pap.example.proguard.service.impl;

import cn.net.pap.example.proguard.entity.AutoIncrePreKey;
import cn.net.pap.example.proguard.repository.AutoIncrePreKeyRepository;
import cn.net.pap.example.proguard.service.IAutoIncrePreKeyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AutoIncrePreKeyServiceImpl implements IAutoIncrePreKeyService {

    @Autowired
    private AutoIncrePreKeyRepository autoIncrePreKeyRepository;

    @Override
    public AutoIncrePreKey saveAndFlush(AutoIncrePreKey entity) {
        return autoIncrePreKeyRepository.saveAndFlush(entity);
    }

    @Override
    public AutoIncrePreKey saveAndFlushThrowException(AutoIncrePreKey entity) {
        AutoIncrePreKey autoIncrePreKey = autoIncrePreKeyRepository.saveAndFlush(entity);
        int i = 1/0;
        return autoIncrePreKey;
    }

    @Override
    public List<AutoIncrePreKey> findAll() {
        return autoIncrePreKeyRepository.findAll();
    }

}
