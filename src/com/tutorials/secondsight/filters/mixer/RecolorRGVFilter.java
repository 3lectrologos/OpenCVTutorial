package com.tutorials.secondsight.filters.mixer;

import java.util.ArrayList;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.Mat;

import com.tutorials.secondsight.filters.Filter;

public class RecolorRGVFilter implements Filter {
  @Override
  public void apply(Mat src, Mat dst) {
    final List<Mat> mChannels = new ArrayList<Mat>(4);
    Core.split(src, mChannels);
    final Mat r = mChannels.get(0);
    final Mat g = mChannels.get(1);
    final Mat b = mChannels.get(2);
    Core.min(b, r, b);
    Core.min(b, g, b);
    mChannels.set(2, b);
    Core.merge(mChannels, dst);
  }
}
