package cn.net.pap.common.datastructure.designPattern;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DecoratorPattern {

    private static final Logger log = LoggerFactory.getLogger(DecoratorPattern.class);

    interface Shape {
        void draw();
    }

    class Rectangle implements Shape {

        @Override
        public void draw() {
            log.info("Shape: Rectangle");
        }
    }

    class Circle implements Shape {

        @Override
        public void draw() {
            log.info("Shape: Circle");
        }
    }

    abstract class ShapeDecorator implements Shape {
        protected Shape decoratedShape;

        public ShapeDecorator(Shape decoratedShape) {
            this.decoratedShape = decoratedShape;
        }

        public void draw() {
            decoratedShape.draw();
        }
    }

    class RedShapeDecorator extends ShapeDecorator {

        public RedShapeDecorator(Shape decoratedShape) {
            super(decoratedShape);
        }

        @Override
        public void draw() {
            decoratedShape.draw();
            setRedBorder(decoratedShape);
        }

        private void setRedBorder(Shape decoratedShape) {
            log.info("Border Color: Red");
        }
    }

    @Test
    public void test() {

        Shape circle = new Circle();
        ShapeDecorator redCircle = new RedShapeDecorator(new Circle());
        ShapeDecorator redRectangle = new RedShapeDecorator(new Rectangle());
        //Shape redCircle = new RedShapeDecorator(new Circle());
        //Shape redRectangle = new RedShapeDecorator(new Rectangle());
        log.info("Circle with normal border");
        circle.draw();

        log.info("Circle of red border");
        redCircle.draw();

        log.info("Rectangle of red border");
        redRectangle.draw();
    }

}
