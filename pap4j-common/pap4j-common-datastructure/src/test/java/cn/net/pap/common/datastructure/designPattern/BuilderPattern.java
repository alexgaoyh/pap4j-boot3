package cn.net.pap.common.datastructure.designPattern;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class BuilderPattern {

    interface Item {
        public String name();

        public Packing packing();

        public float price();
    }

    interface Packing {
        public String pack();
    }

    static class Wrapper implements Packing {

        @Override
        public String pack() {
            return "Wrapper";
        }
    }

    static class Bottle implements Packing {

        @Override
        public String pack() {
            return "Bottle";
        }
    }

    static abstract class Burger implements Item {

        @Override
        public Packing packing() {
            return new Wrapper();
        }

        @Override
        public abstract float price();
    }

    static abstract class ColdDrink implements Item {

        @Override
        public Packing packing() {
            return new Bottle();
        }

        @Override
        public abstract float price();
    }

    static class VegBurger extends Burger {

        @Override
        public float price() {
            return 25.0f;
        }

        @Override
        public String name() {
            return "Veg Burger";
        }
    }

    static class ChickenBurger extends Burger {

        @Override
        public float price() {
            return 50.5f;
        }

        @Override
        public String name() {
            return "Chicken Burger";
        }
    }

    static class Coke extends ColdDrink {

        @Override
        public float price() {
            return 30.0f;
        }

        @Override
        public String name() {
            return "Coke";
        }
    }

    static class Pepsi extends ColdDrink {

        @Override
        public float price() {
            return 35.0f;
        }

        @Override
        public String name() {
            return "Pepsi";
        }
    }

    static class Meal {
        private List<Item> items = new ArrayList<Item>();

        public void addItem(Item item) {
            items.add(item);
        }

        public float getCost() {
            float cost = 0.0f;
            for (Item item : items) {
                cost += item.price();
            }
            return cost;
        }

        public void showItems() {
            for (Item item : items) {
                System.out.print("Item : " + item.name());
                System.out.print(", Packing : " + item.packing().pack());
                System.out.println(", Price : " + item.price());
            }
        }
    }

    static class MealBuilder {

        public Meal prepareVegMeal() {
            Meal meal = new Meal();
            meal.addItem(new VegBurger());
            meal.addItem(new Coke());
            return meal;
        }

        public Meal prepareNonVegMeal() {
            Meal meal = new Meal();
            meal.addItem(new ChickenBurger());
            meal.addItem(new Pepsi());
            return meal;
        }
    }

    @Test
    public void test() {
        MealBuilder mealBuilder = new MealBuilder();

        Meal vegMeal = mealBuilder.prepareVegMeal();
        System.out.println("Veg Meal");
        vegMeal.showItems();
        System.out.println("Total Cost: " + vegMeal.getCost());

        Meal nonVegMeal = mealBuilder.prepareNonVegMeal();
        System.out.println("\n\nNon-Veg Meal");
        nonVegMeal.showItems();
        System.out.println("Total Cost: " + nonVegMeal.getCost());
    }


}
