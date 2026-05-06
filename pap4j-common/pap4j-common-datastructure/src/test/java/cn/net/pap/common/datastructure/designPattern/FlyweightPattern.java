package cn.net.pap.common.datastructure.designPattern;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

public class FlyweightPattern {

    private static final Logger log = LoggerFactory.getLogger(FlyweightPattern.class);

    interface Shape {
        void draw();
    }

    static class Circle implements Shape {
        private String color;
        private int x;
        private int y;
        private int radius;

        public Circle(String color) {
            this.color = color;
        }

        public void setX(int x) {
            this.x = x;
        }

        public void setY(int y) {
            this.y = y;
        }

        public void setRadius(int radius) {
            this.radius = radius;
        }

        @Override
        public void draw() {
            log.info("Circle: Draw() [Color : {}, x : {}, y : {}, radius : {}]", color, x, y, radius);
        }
    }

    static class ShapeFactory {
        private static final HashMap<String, Shape> circleMap = new HashMap<>();

        public static Shape getCircle(String color) {
            Circle circle = (Circle) circleMap.get(color);

            if (circle == null) {
                circle = new Circle(color);
                circleMap.put(color, circle);
                log.info("Creating circle of color : {}", color);
            }
            return circle;
        }
    }

    private static final String colors[] =
            {"Red", "Green", "Blue", "White", "Black"};

    @Test
    public void test() {

        for (int i = 0; i < 20; ++i) {
            String color = colors[(int) (Math.random() * colors.length)];
            Circle circle =
                    (Circle) ShapeFactory.getCircle(color);
            circle.setX((int) (Math.random() * 100));
            circle.setY((int) (Math.random() * 100));
            circle.setRadius(100);
            circle.draw();
        }
    }


}
