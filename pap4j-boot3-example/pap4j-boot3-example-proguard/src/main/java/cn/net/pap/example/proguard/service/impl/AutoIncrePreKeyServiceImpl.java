package cn.net.pap.example.proguard.service.impl;

import cn.net.pap.example.proguard.entity.AutoIncrePreKey;
import cn.net.pap.example.proguard.repository.AutoIncrePreKeyRepository;
import cn.net.pap.example.proguard.service.IAutoIncrePreKeyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
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
    @Transactional
    public AutoIncrePreKey saveAndFlushThrowRuntimeException(AutoIncrePreKey entity) throws RuntimeException {
        try {
            AutoIncrePreKey autoIncrePreKey = autoIncrePreKeyRepository.saveAndFlush(entity);
            throw new RuntimeException("ASDF");
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 非受检异常（RuntimeException, Error）→ 回滚
     * 受检异常（Checked Exception）→ 不回滚    本例的 IOException
     * @param entity
     * @return
     * @throws IOException
     */
    @Override
    @Transactional
    public AutoIncrePreKey saveAndFlushThrowIOException(AutoIncrePreKey entity) throws IOException {
        try {
            AutoIncrePreKey autoIncrePreKey = autoIncrePreKeyRepository.saveAndFlush(entity);
            throw new IOException("ASDF");
        } catch (IOException e) {
            throw new IOException(e);
        }
    }

    @Override
    public List<AutoIncrePreKey> findAll() {
        return autoIncrePreKeyRepository.findAll();
    }

}
