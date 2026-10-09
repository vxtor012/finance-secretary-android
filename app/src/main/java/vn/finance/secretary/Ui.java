package vn.finance.secretary;

import android.content.Context;
import android.view.View;
import android.widget.*;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.TextInputLayout;

final class Ui {
  static int color(Context c, int attr) {
    return MaterialColors.getColor(c, attr, "FinanceSecretary");
  }

  static int dp(Context c, int n) {
    return Math.round(c.getResources().getDisplayMetrics().density * n);
  }

  static class Form extends LinearLayout {
    Form(Context c) {
      super(c);
      setOrientation(VERTICAL);
      setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 16));
    }

    @Override
    public void addView(View child) {
      if (child instanceof EditText) {
        EditText e = (EditText) child;
        TextInputLayout box = new TextInputLayout(getContext());
        box.setHint(e.getHint());
        e.setHint(null);
        box.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        box.setBoxStrokeWidth(0);
        box.setBoxStrokeWidthFocused(dp(getContext(), 1));
        box.setBoxBackgroundColor(
            color(getContext(), com.google.android.material.R.attr.colorSurfaceContainerLow));
        box.setBoxCornerRadii(
            dp(getContext(), 14), dp(getContext(), 14), dp(getContext(), 14), dp(getContext(), 14));
        e.setBackground(null);
        e.setPadding(
            dp(getContext(), 16), dp(getContext(), 16), dp(getContext(), 16), dp(getContext(), 16));
        box.addView(e, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(getContext(), 16);
        super.addView(box, p);
      } else {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(getContext(), 8);
        super.addView(child, p);
      }
    }
  }
}
