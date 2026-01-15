package cn.net.pap.common.datastructure.chroniclemap.dto;

import net.openhft.chronicle.wire.SelfDescribingMarshallable;

public class TermStatDTO extends SelfDescribingMarshallable {

    public long globalTotalCount = 0; // 该词在所有文档中出现的总次数

    public long docCount = 0;        // 包含该词的文档数量


}