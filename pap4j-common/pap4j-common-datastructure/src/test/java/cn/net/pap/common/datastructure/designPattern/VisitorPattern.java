package cn.net.pap.common.datastructure.designPattern;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VisitorPattern {

    private static final Logger log = LoggerFactory.getLogger(VisitorPattern.class);

    interface ComputerPart {
        public void accept(ComputerPartVisitor computerPartVisitor);
    }

    class Keyboard implements ComputerPart {

        @Override
        public void accept(ComputerPartVisitor computerPartVisitor) {
            computerPartVisitor.visit(this);
        }
    }

    class Monitor implements ComputerPart {

        @Override
        public void accept(ComputerPartVisitor computerPartVisitor) {
            computerPartVisitor.visit(this);
        }
    }

    class Mouse implements ComputerPart {

        @Override
        public void accept(ComputerPartVisitor computerPartVisitor) {
            computerPartVisitor.visit(this);
        }
    }

    class Computer implements ComputerPart {

        ComputerPart[] parts;

        public Computer() {
            parts = new ComputerPart[]{new Mouse(), new Keyboard(), new Monitor()};
        }


        @Override
        public void accept(ComputerPartVisitor computerPartVisitor) {
            for (int i = 0; i < parts.length; i++) {
                parts[i].accept(computerPartVisitor);
            }
            computerPartVisitor.visit(this);
        }
    }

    interface ComputerPartVisitor {
        public void visit(Computer computer);

        public void visit(Mouse mouse);

        public void visit(Keyboard keyboard);

        public void visit(Monitor monitor);
    }

    class ComputerPartDisplayVisitor implements ComputerPartVisitor {

        @Override
        public void visit(Computer computer) {
            log.info("Displaying Computer.");
        }

        @Override
        public void visit(Mouse mouse) {
            log.info("Displaying Mouse.");
        }

        @Override
        public void visit(Keyboard keyboard) {
            log.info("Displaying Keyboard.");
        }

        @Override
        public void visit(Monitor monitor) {
            log.info("Displaying Monitor.");
        }
    }

    @Test
    public void test() {
        ComputerPart computer = new Computer();
        computer.accept(new ComputerPartDisplayVisitor());
    }


}
