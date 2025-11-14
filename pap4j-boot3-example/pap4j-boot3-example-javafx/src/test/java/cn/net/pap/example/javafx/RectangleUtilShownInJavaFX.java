package cn.net.pap.example.javafx;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * 矩形框在画布中的数据展示
 * value from
 * class : cn.net.pap.common.datastructure.catalog.RectangleUtilTest.java
 * method : mergeBoxesTest
 */
public class RectangleUtilShownInJavaFX extends Application {

    // --module-path "D:\.jdks\javafx-sdk-17.0.17\lib" --add-modules javafx.controls,javafx.fxml
//    public static void main(String[] args) {
//        launch(args);
//    }

    @Override
    public void start(Stage primaryStage) {
        // 创建画布
        Canvas canvas = new Canvas(800, 600);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // 绘制坐标系和矩形
        drawCoordinateSystem(gc);
        drawRectangle(gc, 10.0, 40.0, 0.0, 20.0);
        drawRectangle(gc, 52.0, 80.0, 0.0, 20.0);
        drawRectangle(gc, 15.0, 50.0, 25.0, 45.0);
        drawRectangle(gc, 60.0, 90.0, 25.0, 45.0);
        drawRectangle(gc, 10.0, 50.0, 50.0, 70.0);

        // 创建布局并添加画布
        Pane root = new Pane(canvas);
        Scene scene = new Scene(root, 800, 600);

        // 设置舞台
        primaryStage.setTitle("坐标系和矩形绘制");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    /**
     * 绘制坐标系
     */
    private void drawCoordinateSystem(GraphicsContext gc) {
        // 设置坐标系颜色
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(1.5);

        // 绘制X轴和Y轴（从左上角原点开始）
        gc.strokeLine(0, 0, 700, 0); // X轴
        gc.strokeLine(0, 0, 0, 500); // Y轴

        // 设置坐标标签颜色
        gc.setFill(Color.BLUE);
        gc.setFont(javafx.scene.text.Font.font("Arial", 12));

        // 在X轴上标记坐标点
        for (int i = 0; i <= 600; i += 50) {
            gc.strokeLine(i, -5, i, 5); // X轴刻度
            gc.fillText(String.valueOf(i), i - 5, 20); // X轴坐标标签
        }

        // 在Y轴上标记坐标点
        for (int i = 0; i <= 400; i += 50) {
            gc.strokeLine(-5, i, 5, i); // Y轴刻度
            gc.fillText(String.valueOf(i), 10, i + 5); // Y轴坐标标签
        }

        // 标记原点
        gc.setFill(Color.RED);
        gc.fillText("原点 (0,0)", 5, 15);
    }

    /**
     * 绘制矩形 - 按照画布坐标系
     */
    private void drawRectangle(GraphicsContext gc, Double x1, Double x2, Double y1, Double y2) {
        // 设置矩形样式
        gc.setFill(Color.rgb(255, 0, 0, 0.3)); // 半透明红色填充
        gc.setStroke(Color.RED); // 红色边框
        gc.setLineWidth(2);

        // 计算矩形的宽度和高度
        double width = x2 - x1;
        double height = y2 - y1;

        // 直接使用画布坐标系绘制矩形
        gc.fillRect(x1, y1, width, height);
        gc.strokeRect(x1, y1, width, height);
    }

}