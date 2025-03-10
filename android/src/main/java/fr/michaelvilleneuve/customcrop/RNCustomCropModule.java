
package fr.michaelvilleneuve.customcrop;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.WritableMap;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.imgcodecs.*;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import org.opencv.calib3d.Calib3d;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class RNCustomCropModule extends ReactContextBaseJavaModule {

  private final ReactApplicationContext reactContext;

  public RNCustomCropModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;

    if (!OpenCVLoader.initDebug())
      Log.e("React-Native-Perspective-Image-Cropper :: OpenCV", "💥 Unable to load OpenCV");
  }

  @Override
  public String getName() {
    return "CustomCropManager";
  }

  @ReactMethod
  public void crop(ReadableMap points, String imageUri, Callback callback) throws IOException {

    Point tl = new Point(points.getMap("topLeft").getDouble("x"), points.getMap("topLeft").getDouble("y"));
    Point tr = new Point(points.getMap("topRight").getDouble("x"), points.getMap("topRight").getDouble("y"));
    Point bl = new Point(points.getMap("bottomLeft").getDouble("x"), points.getMap("bottomLeft").getDouble("y"));
    Point br = new Point(points.getMap("bottomRight").getDouble("x"), points.getMap("bottomRight").getDouble("y"));

    Size calculatedSize = new Size(points.getDouble("width"), points.getDouble("height"));

    Mat src = Imgcodecs.imread(imageUri.replace("file://", ""), Imgproc.COLOR_BGR2RGB);
    Imgproc.cvtColor(src, src, Imgproc.COLOR_BGR2RGB);
   
    boolean ratioAlreadyApplied = src.size().width > calculatedSize.width; //tr.x * (src.size().width / calculatedSize.width) < src.size().width;
    double ratio = ratioAlreadyApplied ? src.size().width / calculatedSize.width : 1;

    double widthA = Math.sqrt(Math.pow(br.x - bl.x, 2) + Math.pow(br.y - bl.y, 2));
    double widthB = Math.sqrt(Math.pow(tr.x - tl.x, 2) + Math.pow(tr.y - tl.y, 2));

    double dw = Math.max(widthA, widthB) * ratio;
    int maxWidth = Double.valueOf(dw).intValue();

    double heightA = Math.sqrt(Math.pow(tr.x - br.x, 2) + Math.pow(tr.y - br.y, 2));
    double heightB = Math.sqrt(Math.pow(tl.x - bl.x, 2) + Math.pow(tl.y - bl.y, 2));

    double dh = Math.max(heightA, heightB) * ratio;
    int maxHeight = Double.valueOf(dh).intValue();

    Mat doc = new Mat(maxHeight, maxWidth, CvType.CV_8UC4);

    Mat src_mat = new Mat(4, 1, CvType.CV_32FC2);
    Mat dst_mat = new Mat(4, 1, CvType.CV_32FC2);

    src_mat.put(0, 0, tl.x * ratio, tl.y * ratio, tr.x * ratio, tr.y * ratio, br.x * ratio, br.y * ratio, bl.x * ratio,
        bl.y * ratio);
    dst_mat.put(0, 0, 0.0, 0.0, dw, 0.0, dw, dh, 0.0, dh);

    Mat m = Imgproc.getPerspectiveTransform(src_mat, dst_mat);

    Imgproc.warpPerspective(src, doc, m, doc.size());

    Bitmap bitmap = Bitmap.createBitmap(doc.cols(), doc.rows(), Bitmap.Config.ARGB_8888);
    Utils.matToBitmap(doc, bitmap);

    FileOutputStream fos = null;
    String uuid = UUID.randomUUID().toString();
    final File directory = reactContext.getCacheDir();

    File imageFile = new File(directory,uuid + "." + "JPEG");
    try {
      imageFile.createNewFile();
      fos = new FileOutputStream(imageFile);
      bitmap.compress(Bitmap.CompressFormat.PNG, 70, fos);
    } catch(IOException ioe) {
      ioe.printStackTrace();
    } finally {
      if (fos != null) {
        fos.close();
      }
    }

    WritableMap map = Arguments.createMap();
    map.putString("image", "file://" + imageFile.getAbsolutePath());
    callback.invoke(null, map);

    m.release();
  }

}