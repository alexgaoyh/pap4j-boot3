package cn.net.pap.common.deeplearning4j;

import org.deeplearning4j.datasets.iterator.impl.MnistDataSetIterator;
import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.BackpropType;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.deeplearning4j.ui.api.UIServer;
import org.junit.jupiter.api.Test;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.learning.config.Nesterovs;
import org.nd4j.linalg.lossfunctions.LossFunctions;

public class SimpleDL4JExample {

    // 训练一个简单的多层感知机（MLP）来识别 MNIST 数据集中的手写数字
    @Test
    public void MNIST() throws Exception {
        int batchSize = 128;
        int numEpochs = 15;
        int seed = 123;
        int numInputs = 28 * 28;
        int numOutputs = 10;
        int numHiddenNodes = 100;

        // 加载 MNIST 数据集
        MnistDataSetIterator mnistTrain = new MnistDataSetIterator(batchSize, true, seed);
        MnistDataSetIterator mnistTest = new MnistDataSetIterator(batchSize, false, seed);

        // 配置神经网络
        MultiLayerNetwork model = new MultiLayerNetwork(new NeuralNetConfiguration.Builder()
                .seed(seed)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .updater(new Nesterovs(0.006, 0.9))
                .list()
                .layer(0, new DenseLayer.Builder()
                        .nIn(numInputs)
                        .nOut(numHiddenNodes)
                        .activation(Activation.RELU)
                        .weightInit(org.deeplearning4j.nn.weights.WeightInit.XAVIER)
                        .build())
                .layer(1, new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
                        .nIn(numHiddenNodes)
                        .nOut(numOutputs)
                        .activation(Activation.SOFTMAX)
                        .weightInit(org.deeplearning4j.nn.weights.WeightInit.XAVIER)
                        .build())
                .backpropType(BackpropType.Standard)
                .build()
        );
        model.init();
        model.setListeners(new ScoreIterationListener(10)); // 每 10 次迭代打印分数

        // 设置 UI 服务器（仅用于可选的训练过程可视化）
        UIServer uiServer = UIServer.getInstance();
        uiServer.enableRemoteListener();

        // 训练模型
        for (int i = 0; i < numEpochs; i++) {
            model.fit(mnistTrain);
            System.out.println("Epoch " + i + " complete. Evaluating model...");
            Evaluation eval = model.evaluate(mnistTest);
            System.out.println(eval.stats());
        }

        // 关闭 UI 服务器
        uiServer.stop();
    }
}

