package cn.net.pap.common.datastructure.chroniclemap.dto;

import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;

public class ValueDataNeighborsDTO implements BytesMarshallable {

    public long[] neighbors;

    @Override
    public void writeMarshallable(BytesOut<?> out) {
        out.writeInt(neighbors.length);
        for (long l : neighbors) out.writeLong(l);
    }

    @Override
    public void readMarshallable(BytesIn<?> in) {
        int size = in.readInt();
        neighbors = new long[size];
        for (int i = 0; i < size; i++) {
            neighbors[i] = in.readLong();
        }
    }
}