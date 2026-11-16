package vn.finance.secretary;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import java.util.List;

/** Scalable artwork and visual data for the finance overview. */
final class RonasVisuals {
  private RonasVisuals() {}

  static final class BalanceGradient extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    BalanceGradient(Context context) {
      super(context);
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onDraw(Canvas canvas) {
      float w = getWidth(), h = getHeight();
      if (w == 0 || h == 0) return;
      paint.setShader(
          new LinearGradient(
              0,
              h,
              w,
              0,
              new int[] {
                0xff124338,
                0xff176451,
                0xff369171,
                0xffa1d7b7,
                0xff5b9d82
              },
              null,
              Shader.TileMode.CLAMP));
      canvas.drawRect(0, 0, w, h, paint);
      glow(canvas, w, h, .74f, .32f, .57f, 1.18f, 0xffb3e2c7);
      glow(canvas, w, h, .19f, .37f, .28f, .73f, 0xff09241f);
      glow(canvas, w, h, .26f, .07f, .42f, .36f, 0xff081d17);
      glow(canvas, w, h, .07f, .20f, .35f, .33f, 0x994aa678);
      glow(canvas, w, h, .42f, .94f, .49f, .58f, 0xff25705d);
      glow(canvas, w, h, .43f, .89f, .32f, .35f, 0xff62904b);
      glow(canvas, w, h, .12f, .87f, .28f, .50f, 0xff184e42);
      paint.setShader(
          new LinearGradient(
              0,
              0,
              0,
              h,
              new int[] {Color.TRANSPARENT, Color.TRANSPARENT, 0xce07261e},
              new float[] {0f, .40f, 1f},
              Shader.TileMode.CLAMP));
      canvas.drawRect(0, 0, w, h, paint);
      paint.setShader(null);
    }

    private void glow(
        Canvas canvas,
        float width,
        float height,
        float x,
        float y,
        float horizontal,
        float vertical,
        int color) {
      canvas.save();
      canvas.translate(width * x, height * y);
      canvas.scale(width * horizontal, height * vertical);
      paint.setShader(
          new RadialGradient(
              0, 0, 1, new int[] {color, color & 0x00ffffff}, null, Shader.TileMode.CLAMP));
      canvas.drawRect(-1, -1, 1, 1, paint);
      canvas.restore();
    }
  }

  static final class CardMark extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    CardMark(Context context) {
      super(context);
    }

    @Override
    protected void onDraw(Canvas canvas) {
      float radius = Math.min(getWidth() / 3f, getHeight() / 2f);
      paint.setColor(0x94ffffff);
      canvas.drawCircle(radius, getHeight() / 2f, radius, paint);
      paint.setColor(0xc2ffffff);
      canvas.drawCircle(getWidth() - radius, getHeight() / 2f, radius, paint);
    }
  }

  record Bar(String label, long amount) {}

  static final class Bars extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Bar> bars;
    private final Typeface typeface;
    private int selected;
    private double maximum, minimum;

    Bars(Context context, List<Bar> bars, Typeface typeface) {
      super(context);
      this.bars = List.copyOf(bars);
      this.typeface = typeface;
      StringBuilder detail = new StringBuilder("Biểu đồ. ");
      for (int i = 0; i < bars.size(); i++) {
        Bar bar = bars.get(i);
        if (Math.abs((double) bar.amount()) > Math.abs((double) bars.get(selected).amount()))
          selected = i;
        maximum = Math.max(maximum, bar.amount());
        minimum = Math.min(minimum, bar.amount());
        detail.append(bar.label()).append(": ").append(Ledger.money(bar.amount())).append(". ");
      }
      setContentDescription(detail.toString());
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    @Override
    protected void onDraw(Canvas canvas) {
      if (bars.isEmpty()) return;
      float bottom = getHeight() - dp(24), top = dp(36);
      if (bottom <= top) return;
      double span = Math.max(1, maximum - minimum);
      float zero = (float) (top + maximum / span * (bottom - top));
      float slice = getWidth() / (float) bars.size();
      float width = Math.min(dp(38), slice * .77f);
      paint.setTypeface(typeface);
      paint.setTextSize(dp(12));
      paint.setTextAlign(Paint.Align.CENTER);
      for (int i = 0; i < bars.size(); i++) {
        Bar bar = bars.get(i);
        float x = slice * (i + .5f);
        float y = (float) (top + (maximum - bar.amount()) / span * (bottom - top));
        float start = Math.min(y, zero), end = Math.max(y, zero);
        paint.setColor(
            getContext()
                .getColor(i == selected ? R.color.ronas_chart_selected : R.color.ronas_chart_bar));
        canvas.drawRoundRect(
            x - width / 2,
            start,
            x + width / 2,
            Math.max(end, start + dp(3)),
            dp(11),
            dp(11),
            paint);
        paint.setColor(getContext().getColor(R.color.app_on_surface));
        canvas.drawText(bar.label(), x, getHeight() - dp(5), paint);
        if (selected == i && bar.amount() != 0) tooltip(canvas, x, start, bar.amount());
      }
    }

    private void tooltip(Canvas canvas, float x, float y, long amount) {
      String text = Ledger.money(amount);
      float half = paint.measureText(text) / 2 + dp(8);
      x = Math.max(half, Math.min(getWidth() - half, x));
      RectF tag =
          new RectF(
              x - half,
              Math.max(dp(2), y - dp(27)),
              x + half,
              Math.max(dp(2), y - dp(27)) + dp(21));
      paint.setColor(Color.WHITE);
      canvas.drawRoundRect(tag, dp(6), dp(6), paint);
      paint.setColor(getContext().getColor(R.color.ronas_dark));
      canvas.drawText(text, x, tag.top + dp(14), paint);
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
      if (bars.isEmpty()) return false;
      if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) return true;
      if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
        selected =
            Math.max(0, Math.min(bars.size() - 1, (int) (event.getX() * bars.size() / getWidth())));
        invalidate();
        performClick();
        return true;
      }
      return true;
    }

    @Override
    public boolean performClick() {
      super.performClick();
      return true;
    }

    private float dp(int value) {
      return Ui.dp(getContext(), value);
    }
  }
}
