package cn.net.pap.example.proguard.service.impl;

import cn.net.pap.example.proguard.entity.Proguard;
import cn.net.pap.example.proguard.repository.ProguardRepository;
import cn.net.pap.example.proguard.service.IProguardService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

@Service
public class ProguardServiceImpl implements IProguardService {

    @Autowired
    private ProguardRepository proguardRepository;

    @Autowired
    private EntityManager entityManager;

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

    @Override
    public List<Proguard> saveAllAndFlush(List<Proguard> proguards) {
        return proguardRepository.saveAllAndFlush(proguards);
    }

    @Override
    public Proguard getProguardByProguardId(Long proguardId) {
        return proguardRepository.getProguardByProguardId(proguardId);
    }

    @Override
    public Proguard updateProguardByProguardId(Proguard proguard) {
        return proguardRepository.saveAndFlush(proguard);
    }

    @Override
    public List<Proguard> searchAllByProguardNameRange(String proguardNameRange) {
        // 解析范围字符串
        List<String> ranges = Arrays.asList(proguardNameRange.split(","));

        // 构建查询
        TypedQuery<Proguard> query = entityManager.createQuery(
                "SELECT e FROM Proguard e WHERE " +
                        "e.proguardName IN :ranges", Proguard.class);

        // 转换范围为整数列表
        List<Long> longRanges = ranges.stream()
                .flatMap(range -> {
                    if (range.contains("-")) {
                        String[] parts = range.split("-");
                        Long start = Long.parseLong(parts[0]);
                        Long end = Long.parseLong(parts[1]);
                        return LongStream.rangeClosed(start, end).boxed();
                    } else {
                        return Stream.of(Long.parseLong(range));
                    }
                })
                .collect(Collectors.toList());

        List<String> strRanges = longRanges.stream().map(String::valueOf).collect(Collectors.toList());

        // 设置参数并执行查询
        query.setParameter("ranges", strRanges);
        return query.getResultList();
    }
}
