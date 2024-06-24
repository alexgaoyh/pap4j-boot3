package cn.net.pap.common.boofcv;

import boofcv.abst.feature.associate.AssociateDescription;
import boofcv.abst.feature.associate.ScoreAssociation;
import boofcv.abst.feature.detdesc.DetectDescribePoint;
import boofcv.abst.feature.detect.interest.ConfigFastHessian;
import boofcv.alg.descriptor.UtilFeature;
import boofcv.alg.feature.associate.AssociateThreeByPairs;
import boofcv.factory.feature.associate.ConfigAssociateGreedy;
import boofcv.factory.feature.associate.FactoryAssociation;
import boofcv.factory.feature.detdesc.FactoryDetectDescribe;
import boofcv.io.UtilIO;
import boofcv.io.image.UtilImageIO;
import boofcv.misc.BoofMiscOps;
import boofcv.struct.feature.AssociatedTripleIndex;
import boofcv.struct.feature.TupleDesc_F64;
import boofcv.struct.geo.AssociatedTriple;
import boofcv.struct.image.GrayU8;
import georegression.struct.point.Point2D_F64;
import org.ddogleg.struct.DogArray;
import org.ddogleg.struct.DogArray_I32;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * 图像拼接，相似点
 * 仿照 https://github.com/lessthanoptimal/BoofCV/blob/v1.1.4/examples/src/main/java/boofcv/examples/features/ExampleAssociateThreeView.java
 */
public class AssociateThreeViewUtil {

    /**
     * 合并两周图像
     * todo 不完备，后续需要根据图像的映射点的坐标的情况，处理不同情况下的图像合并。
     * @param image1Path
     * @param image2Path
     * @param outputPath
     */
    public static void mergeImages(String image1Path, String image2Path,  String outputPath) {
        try {
            DogArray<AssociatedTriple> mappings = associated(image1Path, image2Path);

            BufferedImage image1 = ImageIO.read(new File(image1Path));
            BufferedImage image2 = ImageIO.read(new File(image2Path));

            // 创建一个合并后的图像
            BufferedImage mergedImage = new BufferedImage(image1.getWidth() + image2.getWidth(),
                    image1.getHeight() + image2.getHeight(),
                    BufferedImage.TYPE_INT_RGB);

            // 将第一张图像绘制到合并后的图像中
            Graphics2D g2d = mergedImage.createGraphics();
            g2d.drawImage(image1, 0, 0,  image1.getWidth(), image1.getHeight(), null);

            // 根据映射关系绘制第二张图像的对应部分到合并后的图像中
            for (AssociatedTriple mapping : mappings.toList()) {
                // 计算映射点在合并后图像中的位置
                int x1 = (int) Math.round(mapping.p1.x);
                int y1 = (int) Math.round(mapping.p1.y);
                int x2 = (int) Math.round(mapping.p2.x);
                int y2 = (int) Math.round(mapping.p2.y);

                // 绘制第二张图像的部分到合并后的图像中
                boolean b = g2d.drawImage(image2, x1 - x2, y1 - y2, image2.getWidth(), image2.getHeight(), null);
                System.out.println(b);
            }
            g2d.dispose();

            // 保存合并后的图像
            boolean jpg = ImageIO.write(mergedImage, "jpg", new File(outputPath));
            System.out.println(jpg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 两张图像在拼接过程中的相似点
     *
     * @param imagePath1
     * @param imagePath2
     * @return
     */
    private static DogArray<AssociatedTriple> associated(String imagePath1, String imagePath2) {
        DogArray<Point2D_F64> locations01 = new DogArray<>(Point2D_F64::new);
        DogArray<Point2D_F64> locations02 = new DogArray<>(Point2D_F64::new);
        DogArray<Point2D_F64> locations03 = new DogArray<>(Point2D_F64::new);
        List<DogArray<Point2D_F64>> listLocations = BoofMiscOps.asList(locations01, locations02, locations03);

        DogArray_I32 featureSet01 = new DogArray_I32();
        DogArray_I32 featureSet02 = new DogArray_I32();
        DogArray_I32 featureSet03 = new DogArray_I32();
        List<DogArray_I32> listFeatureSets = BoofMiscOps.asList(featureSet01, featureSet02, featureSet03);

        String name = "AssociateThreeViewUtil_";
        GrayU8 gray01 = UtilImageIO.loadImage(UtilIO.pathExample(imagePath1), GrayU8.class);
        GrayU8 gray02 = UtilImageIO.loadImage(UtilIO.pathExample(imagePath2), GrayU8.class);
        GrayU8 gray03 = UtilImageIO.loadImage(UtilIO.pathExample(imagePath1), GrayU8.class);

        // Using SURF features. Robust and fairly fast to compute
        DetectDescribePoint<GrayU8, TupleDesc_F64> detDesc = FactoryDetectDescribe.surfStable(
                new ConfigFastHessian(0, 4, 1000, 1, 9, 4, 2), null, null, GrayU8.class);

        DogArray<TupleDesc_F64> features01 = UtilFeature.createArray(detDesc, 100);
        DogArray<TupleDesc_F64> features02 = UtilFeature.createArray(detDesc, 100);
        DogArray<TupleDesc_F64> features03 = UtilFeature.createArray(detDesc, 100);
        List<DogArray<TupleDesc_F64>> listFeatures = BoofMiscOps.asList(features01, features02, features03);

        // Compute and describe features inside the image
        detectFeatures(listLocations, listFeatures, listFeatureSets, detDesc, gray01, 0);
        detectFeatures(listLocations, listFeatures, listFeatureSets, detDesc, gray02, 1);
        detectFeatures(listLocations, listFeatures, listFeatureSets, detDesc, gray03, 2);

        // Find features for an association ring across all the views. This removes most false positives.
        DogArray<AssociatedTripleIndex> associatedIdx = threeViewPairwiseAssociate(detDesc, features01, features02, features03, featureSet01, featureSet02, featureSet03);

        // Convert the matched indexes into AssociatedTriple which contain the actual pixel coordinates
        DogArray<AssociatedTriple> associated = new DogArray<>(AssociatedTriple::new);
        associatedIdx.forEach(p -> associated.grow().setTo(
                locations01.get(p.a), locations02.get(p.b), locations03.get(p.c)));

        return associated;
    }

    private static void detectFeatures(List<DogArray<Point2D_F64>> listLocations,
                                       List<DogArray<TupleDesc_F64>> listFeatures,
                                       List<DogArray_I32> listFeatureSets,
                                       DetectDescribePoint<GrayU8, TupleDesc_F64> detDesc,
                                       GrayU8 gray, int which ) {
        DogArray<Point2D_F64> locations = listLocations.get(which);
        DogArray<TupleDesc_F64> features = listFeatures.get(which);
        DogArray_I32 featureSet = listFeatureSets.get(which);

        detDesc.detect(gray);
        for (int i = 0; i < detDesc.getNumberOfFeatures(); i++) {
            Point2D_F64 pixel = detDesc.getLocation(i);
            locations.grow().setTo(pixel.x, pixel.y);
            features.grow().setTo(detDesc.getDescription(i));
            featureSet.add(detDesc.getSet(i));
        }
    }

    private static DogArray<AssociatedTripleIndex> threeViewPairwiseAssociate(
            DetectDescribePoint<GrayU8, TupleDesc_F64> detDesc,
            DogArray<TupleDesc_F64> features01,
            DogArray<TupleDesc_F64> features02,
            DogArray<TupleDesc_F64> features03,
            DogArray_I32 featureSet01,
            DogArray_I32 featureSet02,
            DogArray_I32 featureSet03) {
        ScoreAssociation<TupleDesc_F64> scorer =
                FactoryAssociation.scoreEuclidean(TupleDesc_F64.class, true);
        AssociateDescription<TupleDesc_F64> associate =
                FactoryAssociation.greedy(new ConfigAssociateGreedy(true, 0.1), scorer);

        var associateThree = new AssociateThreeByPairs<>(associate);

        associateThree.initialize(detDesc.getNumberOfSets());
        associateThree.setFeaturesA(features01, featureSet01);
        associateThree.setFeaturesB(features02, featureSet02);
        associateThree.setFeaturesC(features03, featureSet03);

        associateThree.associate();

        return associateThree.getMatches();
    }
}
