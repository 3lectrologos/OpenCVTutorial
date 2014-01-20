package com.tutorials.secondsight.filters.mixer;

import java.util.ArrayList;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.Mat;

import com.tutorials.secondsight.filters.Filter;

public class RecolorRCFilter implements Filter {
  @Override
  public void apply(Mat src, Mat dst) {
    final List<Mat> mChannels = new ArrayList<Mat>(4);
    Core.split(src, mChannels);
    final Mat g = mChannels.get(1);
    final Mat b = mChannels.get(2);
    Core.addWeighted(g, 0.5, b, 0.5, 0, g);
    mChannels.set(2, g);
    Core.merge(mChannels, dst);
  }
}
