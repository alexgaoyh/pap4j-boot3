package cn.net.pap.example.proguard.service.impl;

import cn.net.pap.example.proguard.entity.NumberSegment;
import cn.net.pap.example.proguard.repository.NumberSegmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Queue;

@Service
public class NumberSegmentService {

    private static final int CACHE_SIZE = 10;

    private final NumberSegmentRepository numberSegmentRepository;

    public NumberSegmentService(NumberSegmentRepository numberSegmentRepository) {
        this.numberSegmentRepository = numberSegmentRepository;
    }

    @Transactional
    public void save(NumberSegment numberSegment) {
        numberSegmentRepository.save(numberSegment);
    }

    @Transactional
    public void loadSegments(String segmentName, Queue<String> queue) {
        NumberSegment updatedSegment = getUpdatedSegment(segmentName);
        int startValue = updatedSegment.getCurrentValue() - CACHE_SIZE + 1;
        for (int i = 0; i < CACHE_SIZE; i++) {
            int value = startValue + i;
            String fullNumber = updatedSegment.getSegmentPrefix() + String.format("%04d", value);
            queue.offer(fullNumber);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NumberSegment getUpdatedSegment(String segmentName) {
        numberSegmentRepository.updateCurrentValue(segmentName, CACHE_SIZE);
        return numberSegmentRepository.findByName(segmentName).get();
    }
}
