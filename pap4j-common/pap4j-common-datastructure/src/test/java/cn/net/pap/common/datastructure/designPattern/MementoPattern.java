package cn.net.pap.common.datastructure.designPattern;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class MementoPattern {

    private static final Logger log = LoggerFactory.getLogger(MementoPattern.class);

    class Memento {
        private String state;

        public Memento(String state) {
            this.state = state;
        }

        public String getState() {
            return state;
        }
    }

    class Originator {
        private String state;

        public void setState(String state) {
            this.state = state;
        }

        public String getState() {
            return state;
        }

        public Memento saveStateToMemento() {
            return new Memento(state);
        }

        public void getStateFromMemento(Memento Memento) {
            state = Memento.getState();
        }
    }

    class CareTaker {
        private List<Memento> mementoList = new ArrayList<Memento>();

        public void add(Memento state) {
            mementoList.add(state);
        }

        public Memento get(int index) {
            return mementoList.get(index);
        }
    }

    @Test
    public void test() {
        Originator originator = new Originator();
        CareTaker careTaker = new CareTaker();
        originator.setState("State #1");
        originator.setState("State #2");
        careTaker.add(originator.saveStateToMemento());
        originator.setState("State #3");
        careTaker.add(originator.saveStateToMemento());
        originator.setState("State #4");

        log.info("Current State: {}", originator.getState());
        originator.getStateFromMemento(careTaker.get(0));
        log.info("First saved State: {}", originator.getState());
        originator.getStateFromMemento(careTaker.get(1));
        log.info("Second saved State: {}", originator.getState());
    }

}
