package cn.net.pap.common.datastructure.designPattern;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FactoryPattern {

    private static final Logger log = LoggerFactory.getLogger(FactoryPattern.class);

    interface Shape {
        void draw();
    }

    static class Rectangle implements Shape {

        @Override
        public void draw() {
            log.info("Inside Rectangle::draw() method.");
        }
    }

    static class Square implements Shape {

        @Override
        public void draw() {
            log.info("Inside Square::draw() method.");
        }
    }

    static class Circle implements Shape {

        @Override
        public void draw() {
            log.info("Inside Circle::draw() method.");
        }
    }

    static class ShapeFactory {

        //使用 getShape 方法获取形状类型的对象
        public Shape getShape(String shapeType) {
            if (shapeType == null) {
                return null;
            }
            if (shapeType.equalsIgnoreCase("CIRCLE")) {
                return new Circle();
            } else if (shapeType.equalsIgnoreCase("RECTANGLE")) {
                return new Rectangle();
            } else if (shapeType.equalsIgnoreCase("SQUARE")) {
                return new Square();
            }
            return null;
        }
    }

    @Test
    public void test() {
        ShapeFactory shapeFactory = new ShapeFactory();

        //获取 Circle 的对象，并调用它的 draw 方法
        Shape shape1 = shapeFactory.getShape("CIRCLE");

        //调用 Circle 的 draw 方法
        if (shape1 != null) {
            shape1.draw();
        }

        //获取 Rectangle 的对象，并调用它的 draw 方法
        Shape shape2 = shapeFactory.getShape("RECTANGLE");

        //调用 Rectangle 的 draw 方法
        if (shape2 != null) {
            shape2.draw();
        }

        //获取 Square 的对象，并调用它的 draw 方法
        Shape shape3 = shapeFactory.getShape("SQUARE");

        //调用 Square 的 draw 方法
        if (shape3 != null) {
            shape3.draw();
        }
    }
}
