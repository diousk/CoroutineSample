package com.diousk.coroutinesample.ext;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.NinePatchDrawable;
import android.util.DisplayMetrics;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * User: bgriffey
 * Date: 12/27/12
 * Time: 2:37 PM
 * from https://gist.github.com/knight9999/86bec38071a9e0a781ee
 */
public class NinePatchBitmapFactory {

    private static final int NO_COLOR = 0x00000001;

    private static final int TRANSPARENT_COLOR = 0x00000000;

    public static NinePatchDrawable createNinePatchDrawable(Resources res, Bitmap bitmap) {
        RangeLists rangeLists = checkBitmap(bitmap);
        Bitmap trimedBitmap = trimBitmap(bitmap);
        NinePatchDrawable drawable = createNinePatchWithCapInsets(res, trimedBitmap, rangeLists, null);
        return drawable;
    }


    public static NinePatchDrawable createNinePatchWithCapInsets(Resources res, Bitmap bitmap,
                                                                 RangeLists rangeLists, String srcName) {
        ByteBuffer buffer = getByteBuffer(rangeLists);

        int left = 0, right = 0, top = 0, bottom = 0;
        for (Range range : rangeLists.paddingListX) {
            left = range.start;
            right = bitmap.getWidth() - range.end;
        }

        for (Range range : rangeLists.paddingListY) {
            top = range.start;
            bottom = bitmap.getHeight() - range.end;
        }

        NinePatchDrawable drawable = new NinePatchDrawable(res, bitmap, buffer.array(), new Rect(left, top, right, bottom), srcName);
        return drawable;
    }

    private static ByteBuffer getByteBuffer(RangeLists rangeLists) {
        ByteBuffer buffer = ByteBuffer.allocate(4 + 7 * 4 + 4 * 2 * rangeLists.rangeListX.size() + 4 * 2 * rangeLists.rangeListY.size() + 4 * 9).order(ByteOrder.nativeOrder());
        buffer.put((byte) 0x01); // was serialised
        buffer.put((byte) (rangeLists.rangeListX.size() * 2)); // x div
        buffer.put((byte) (rangeLists.rangeListY.size() * 2)); // y div
        buffer.put((byte) 0x09); // color

        // skip
        buffer.putInt(0);
        buffer.putInt(0);

        // padding
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(0);

        // skip 4 bytes
        buffer.putInt(0);

        for (Range range : rangeLists.rangeListX) {
            buffer.putInt(range.start);
            buffer.putInt(range.end);
        }
        for (Range range : rangeLists.rangeListY) {
            buffer.putInt(range.start);
            buffer.putInt(range.end);
        }
        buffer.putInt(NO_COLOR);
        buffer.putInt(NO_COLOR);
        buffer.putInt(NO_COLOR);
        buffer.putInt(NO_COLOR);
        buffer.putInt(NO_COLOR);
        buffer.putInt(NO_COLOR);
        buffer.putInt(NO_COLOR);
        buffer.putInt(NO_COLOR);
        buffer.putInt(NO_COLOR);

        return buffer;
    }

    public static class RangeLists {
        public List<Range> rangeListX;
        public List<Range> rangeListY;
        public List<Range> paddingListX;
        public List<Range> paddingListY;
    }

    public static class Range {
        public int start;
        public int end;
    }

    public static RangeLists checkBitmap(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        Timber.d("bitmap width: " + width + ", bitmap height: " + height);

        List<Range> rangeListX = new ArrayList<Range>();

        int pos = -1;
        for (int i = 1; i < width - 1; i++) {
            int color = bitmap.getPixel(i, 0);
            int alpha = Color.alpha(color);
            int red = Color.red(color);
            int green = Color.green(color);
            int blue = Color.blue(color);
            if (alpha == 255 && red == 0 && green == 0 && blue == 0) {
                if (pos == -1) {
                    pos = i - 1;
                }
            } else {
                if (pos != -1) {
                    Range range = new Range();
                    range.start = pos;
                    range.end = i - 1;
                    rangeListX.add(range);
                    pos = -1;
                }
            }
        }
        if (pos != -1) {
            Range range = new Range();
            range.start = pos;
            range.end = width - 2;
            rangeListX.add(range);
        }
        for (Range range : rangeListX) {
            Timber.d("rangeListX range.start: " + range.start + ", range.end: " + range.end);
        }

        List<Range> paddingListX = new ArrayList<Range>();
        pos = -1;
        for (int i = 1; i < width - 1; i++) {
            int color = bitmap.getPixel(i, height - 1);
            int alpha = Color.alpha(color);
            int red = Color.red(color);
            int green = Color.green(color);
            int blue = Color.blue(color);
            if (alpha == 255 && red == 0 && green == 0 && blue == 0) {
                if (pos == -1) {
                    pos = i - 1;
                }
            } else {
                if (pos != -1) {
                    Range range = new Range();
                    range.start = pos;
                    range.end = i - 1;
                    paddingListX.add(range);
                    pos = -1;
                }
            }
        }
        if (pos != -1) {
            Range range = new Range();
            range.start = pos;
            range.end = width - 2;
            paddingListX.add(range);
        }
        for (Range range : paddingListX) {
            Timber.d("paddingListX range.start: " + range.start + ", range.end: " + range.end);
        }

        List<Range> rangeListY = new ArrayList<Range>();

        pos = -1;
        for (int i = 1; i < height - 1; i++) {
            int color = bitmap.getPixel(0, i);
            int alpha = Color.alpha(color);
            int red = Color.red(color);
            int green = Color.green(color);
            int blue = Color.blue(color);
            if (alpha == 255 && red == 0 && green == 0 && blue == 0) {
                if (pos == -1) {
                    pos = i - 1;
                }
            } else {
                if (pos != -1) {
                    Range range = new Range();
                    range.start = pos;
                    range.end = i - 1;
                    rangeListY.add(range);
                    pos = -1;
                }
            }

        }
        if (pos != -1) {
            Range range = new Range();
            range.start = pos;
            range.end = height - 2;
            rangeListY.add(range);
        }
        for (Range range : rangeListY) {
            Timber.d("rangeListY range.start: " + range.start + ", range.end: " + range.end);
        }

        List<Range> paddingListY = new ArrayList<Range>();

        pos = -1;
        for (int i = 1; i < height - 1; i++) {
            int color = bitmap.getPixel(width - 1, i);
            int alpha = Color.alpha(color);
            int red = Color.red(color);
            int green = Color.green(color);
            int blue = Color.blue(color);
            if (alpha == 255 && red == 0 && green == 0 && blue == 0) {
                if (pos == -1) {
                    pos = i - 1;
                }
            } else {
                if (pos != -1) {
                    Range range = new Range();
                    range.start = pos;
                    range.end = i - 1;
                    paddingListY.add(range);
                    pos = -1;
                }
            }

        }
        if (pos != -1) {
            Range range = new Range();
            range.start = pos;
            range.end = height - 2;
            paddingListY.add(range);
        }
        for (Range range : paddingListY) {
            Timber.d("paddingListY range.start: " + range.start + ", range.end: " + range.end);
        }

        RangeLists rangeLists = new RangeLists();
        rangeLists.rangeListX = rangeListX;
        rangeLists.rangeListY = rangeListY;
        rangeLists.paddingListX = paddingListX;
        rangeLists.paddingListY = paddingListY;
        return rangeLists;
    }

    public static Bitmap trimBitmap(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        Bitmap result = Bitmap.createBitmap(bitmap, 1, 1, width - 2, height - 2);

        return result;

    }

    public static Bitmap loadBitmap(File file) {
        BufferedInputStream bis = null;
        try {
            bis = new BufferedInputStream(new FileInputStream(file));
            return BitmapFactory.decodeStream(bis);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                bis.close();
            } catch (Exception e) {

            }
        }
        return null;
    }

    public static String getDensityPostfix(Resources res) {
        String result = null;
        switch (res.getDisplayMetrics().densityDpi) {
            case DisplayMetrics.DENSITY_LOW:
                result = "ldpi";
                break;
            case DisplayMetrics.DENSITY_MEDIUM:
                result = "mdpi";
                break;
            case DisplayMetrics.DENSITY_HIGH:
                result = "hdpi";
                break;
            case DisplayMetrics.DENSITY_XHIGH:
                result = "xhdpi";
                break;
            case DisplayMetrics.DENSITY_XXHIGH:
                result = "xxhdpi";
                break;
            case DisplayMetrics.DENSITY_XXXHIGH:
                result = "xxxhdpi";
                break;
        }
        return result;
    }
}
