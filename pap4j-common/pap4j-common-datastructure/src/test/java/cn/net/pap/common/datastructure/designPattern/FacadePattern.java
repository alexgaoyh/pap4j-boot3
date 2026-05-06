package cn.net.pap.common.datastructure.designPattern;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FacadePattern {

    private static final Logger log = LoggerFactory.getLogger(FacadePattern.class);

    interface Shape {
        void draw();
    }

    class Rectangle implements Shape {

        @Override
        public void draw() {
            log.info("Rectangle::draw()");
        }
    }

    class Square implements Shape {

        @Override
        public void draw() {
            log.info("Square::draw()");
        }
    }

    class Circle implements Shape {

        @Override
        public void draw() {
            log.info("Circle::draw()");
        }
    }

    class ShapeMaker {
        private Shape circle;
        private Shape rectangle;
        private Shape square;

        public ShapeMaker() {
            circle = new Circle();
            rectangle = new Rectangle();
            square = new Square();
        }

        public void drawCircle() {
            circle.draw();
        }

        public void drawRectangle() {
            rectangle.draw();
        }

        public void drawSquare() {
            square.draw();
        }
    }

    @Test
    public void test() {
        ShapeMaker shapeMaker = new ShapeMaker();

        shapeMaker.drawCircle();
        shapeMaker.drawRectangle();
        shapeMaker.drawSquare();
    }
}
