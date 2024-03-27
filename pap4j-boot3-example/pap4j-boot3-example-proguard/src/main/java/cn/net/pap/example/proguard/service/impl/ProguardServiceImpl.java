package cn.net.pap.example.proguard.service.impl;

import cn.net.pap.example.proguard.entity.Proguard;
import cn.net.pap.example.proguard.repository.ProguardRepository;
import cn.net.pap.example.proguard.service.IProguardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProguardServiceImpl implements IProguardService {

    @Autowired
    private ProguardRepository proguardRepository;

    @Override
    public List<Proguard> searchAllByProguardName(String proguardName) {
        List<Proguard> proguards = proguardRepository.searchAllByProguardName(proguardName);
        return proguards;
    }

    @Override
    public Proguard saveAndFlush(Proguard entity) {
        Proguard proguard = proguardRepository.saveAndFlush(entity);
        return proguard;
    }
}
