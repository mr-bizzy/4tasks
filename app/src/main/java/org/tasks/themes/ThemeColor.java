package org.tasks.themes;

import static org.tasks.themes.ChipColorsKt.contentColor;

import android.app.Activity;
import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.core.os.ParcelCompat;

import org.tasks.R;
import org.tasks.dialogs.ColorPalettePicker.Pickable;

public class ThemeColor implements Pickable {

  public static final Parcelable.Creator<ThemeColor> CREATOR =
      new Parcelable.Creator<>() {
        @Override
        public ThemeColor createFromParcel(Parcel source) {
          return new ThemeColor(source);
        }

        @Override
        public ThemeColor[] newArray(int size) {
          return new ThemeColor[size];
        }
      };

  private final int original;
  private final int colorOnPrimary;
  private final int colorPrimary;
  private final boolean isDark;

  public ThemeColor(Context context, int color) {
    this(context, color, color);
  }

  public ThemeColor(Context context, int original, int color) {
    this.original = original;
    if (color == 0) {
      color = TasksThemeKt.BLUE;
    } else {
      color |= 0xFF000000; // remove alpha
    }
    colorPrimary = color;

    colorOnPrimary = contentColor(colorPrimary);
    isDark = colorOnPrimary != -1; // not white means dark background
  }

  private ThemeColor(Parcel source) {
    colorOnPrimary = source.readInt();
    colorPrimary = source.readInt();
    isDark = ParcelCompat.readBoolean(source);
    original = source.readInt();
  }

  public void applyToNavigationBar(Activity activity) {
    activity.getWindow().setNavigationBarColor(getPrimaryColor());

    View decorView = activity.getWindow().getDecorView();
    int systemUiVisibility = applyLightNavigationBar(decorView.getSystemUiVisibility());
    decorView.setSystemUiVisibility(systemUiVisibility);
  }

  private int applyLightNavigationBar(int flag) {
    return isDark
        ? flag | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        : flag & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
  }

  @Override
  public int getPickerColor() {
    return colorPrimary;
  }

  @Override
  public boolean isFree() {
    switch (original) {
      case -16612153: // family sky blue (0xFF0284C7)
      case -14575885: // blue_500
      case -10453621: // blue_grey_500
      case -14606047: // grey_900
        return true;
      default:
        return false;
    }
  }

  public int getOriginalColor() {
    return original;
  }

  @ColorInt
  public int getPrimaryColor() {
    return colorPrimary;
  }

  @ColorInt
  public int getColorOnPrimary() {
    return colorOnPrimary;
  }

  public boolean isDark() {
    return isDark;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(colorOnPrimary);
    dest.writeInt(colorPrimary);
    ParcelCompat.writeBoolean(dest, isDark);
    dest.writeInt(original);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ThemeColor)) {
      return false;
    }

    ThemeColor that = (ThemeColor) o;

    return original == that.original;
  }

  @Override
  public int hashCode() {
    return original;
  }
}
