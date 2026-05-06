package cn.net.pap.common.datastructure.designPattern;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IteratorPattern {

    private static final Logger log = LoggerFactory.getLogger(IteratorPattern.class);

    interface Iterator {
        public boolean hasNext();

        public Object next();
    }

    interface Container {
        public Iterator getIterator();
    }

    class NameRepository implements Container {
        public String[] names = {"Robert", "John", "Julie", "Lora"};

        @Override
        public Iterator getIterator() {
            return new NameIterator();
        }

        private class NameIterator implements Iterator {

            int index;

            @Override
            public boolean hasNext() {
                if (index < names.length) {
                    return true;
                }
                return false;
            }

            @Override
            public Object next() {
                if (this.hasNext()) {
                    return names[index++];
                }
                return null;
            }
        }
    }

    @Test
    public void test() {
        NameRepository namesRepository = new NameRepository();

        for (Iterator iter = namesRepository.getIterator(); iter.hasNext(); ) {
            String name = (String) iter.next();
            log.info("Name : {}", name);
        }
    }
}
