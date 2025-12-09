package cn.net.pap.common.datastructure.chroniclemap.dto;

import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;

public class ValueDataDTO implements BytesMarshallable {

    // 固定字段
    public int label;
    public double weight;
    public long timestamp;

    // Queue 索引
    public long neighborsIndex = -1;

    @Override
    public void writeMarshallable(BytesOut<?> out) {
        out.writeInt(label);
        out.writeDouble(weight);
        out.writeLong(timestamp);

        out.writeLong(neighborsIndex);
    }

    @Override
    public void readMarshallable(BytesIn<?> in) {
        label = in.readInt();
        weight = in.readDouble();
        timestamp = in.readLong();

        neighborsIndex = in.readLong();
    }

}