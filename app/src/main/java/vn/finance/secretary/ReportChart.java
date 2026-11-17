package vn.finance.secretary;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

final class ReportChart extends LinearLayout {
  record Amounts(long expense, long income) {
    static final Amounts ZERO = new Amounts(0, 0);
  }

  private final int expenseColor, incomeColor, ink, grid;
  private final Typeface typeface;
  private final double minimum, maximum;
  private final TextView details;
  private int selected;

  ReportChart(Context context, List<Amounts> monthly, Typeface typeface) {
    super(context);
    this.typeface = typeface;
    expenseColor = context.getColor(R.color.chart_expense);
    incomeColor = context.getColor(R.color.chart_income);
    ink = context.getColor(R.color.app_secondary_text);
    grid = context.getColor(R.color.app_outline);

    int latest = 0;
    double min = 0, max = 0;
    for (int i = 0; i < monthly.size(); i++) {
      Amounts amounts = monthly.get(i);
      if (amounts.expense() != 0 || amounts.income() != 0) latest = i;
      min = Math.min(min, Math.min(amounts.expense(), amounts.income()));
      max = Math.max(max, Math.max(amounts.expense(), amounts.income()));
    }
    minimum = min;
    maximum = max;
    selected = latest;

    setOrientation(VERTICAL);
    TextView legend = label(13);
    SpannableStringBuilder legendText = new SpannableStringBuilder("● Chi     ● Thu");
    legendText.setSpan(new ForegroundColorSpan(expenseColor), 0, 1, 0);
    int secondDot = legendText.toString().lastIndexOf('●');
    legendText.setSpan(new ForegroundColorSpan(incomeColor), secondDot, secondDot + 1, 0);
    legend.setText(legendText);
    legend.setContentDescription("Chú giải: Chi màu đỏ, Thu màu xanh");
    legend.setPadding(dp(4), dp(2), dp(4), dp(4));
    addView(legend);

    RecyclerView months = new RecyclerView(context);
    LinearLayoutManager manager = new LinearLayoutManager(context, RecyclerView.HORIZONTAL, false);
    months.setLayoutManager(manager);
    months.setHorizontalScrollBarEnabled(true);
    int count = monthly.size();
    months.setAdapter(
        new RecyclerView.Adapter<MonthHolder>() {
          @Override
          public MonthHolder onCreateViewHolder(ViewGroup parent, int type) {
            MonthCell cell = new MonthCell(parent.getContext());
            cell.setLayoutParams(new RecyclerView.LayoutParams(dp(32), -1));
            return new MonthHolder(cell);
          }

          @Override
          public void onBindViewHolder(MonthHolder holder, int position) {
            MonthCell cell = holder.cell;
            Amounts amounts = monthly.get(position);
            cell.getLayoutParams().width = Math.max(dp(22), months.getMeasuredWidth() / count);
            cell.bind(position, amounts);
            cell.setOnClickListener(
                v -> {
                  int tapped = holder.getAdapterPosition();
                  if (tapped == RecyclerView.NO_POSITION || selected == tapped) return;
                  int previous = selected;
                  selected = tapped;
                  updateDetails(tapped, monthly.get(tapped));
                  notifyItemChanged(previous);
                  notifyItemChanged(tapped);
                });
          }

          @Override
          public int getItemCount() {
            return count;
          }
        });
    months.setItemAnimator(null);
    addView(months, new LayoutParams(-1, dp(120)));

    details = label(13);
    details.setPadding(dp(4), dp(6), dp(4), 0);
    details.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    addView(details);
    updateDetails(selected, monthly.get(selected));
  }

  private TextView label(int size) {
    TextView view = new TextView(getContext());
    view.setTypeface(typeface);
    view.setTextColor(ink);
    view.setTextSize(size);
    view.setGravity(Gravity.START);
    return view;
  }

  private String describe(Amounts amounts) {
    return "Chi " + Ledger.money(amounts.expense()) + " · Thu " + Ledger.money(amounts.income());
  }

  private void updateDetails(int monthIndex, Amounts amounts) {
    details.setText("Tháng " + (monthIndex + 1) + " · " + describe(amounts));
  }

  private final class MonthCell extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int monthIndex = -1;
    private Amounts amounts;

    MonthCell(Context context) {
      super(context);
      setFocusable(true);
      paint.setTypeface(typeface);
      paint.setTextAlign(Paint.Align.CENTER);
    }

    void bind(int monthIndex, Amounts amounts) {
      this.monthIndex = monthIndex;
      this.amounts = amounts;
      setSelected(monthIndex == selected);
      setContentDescription("Tháng " + (monthIndex + 1) + ", " + describe(amounts));
      invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
      if (monthIndex < 0) return;
      float center = getWidth() / 2f;
      float top = dp(8), bottom = getHeight() - dp(29);
      double span = maximum - minimum;
      float zero = (float) (bottom + minimum / span * (bottom - top));
      paint.setColor(grid);
      canvas.drawLine(0, zero, getWidth(), zero, paint);
      bar(canvas, center - dp(5), zero, (bottom - top) / span, amounts.expense(), expenseColor);
      bar(canvas, center + dp(5), zero, (bottom - top) / span, amounts.income(), incomeColor);
      paint.setColor(isSelected() ? getContext().getColor(R.color.brand_primary) : ink);
      paint.setTextSize(Math.min(dp(18), 12 * getResources().getDisplayMetrics().scaledDensity));
      paint.setFakeBoldText(isSelected());
      canvas.drawText(String.valueOf(monthIndex + 1), center, getHeight() - dp(8), paint);
      paint.setFakeBoldText(false);
    }

    private void bar(Canvas canvas, float x, float zero, double scale, long amount, int color) {
      paint.setColor(color);
      float height = amount == 0 ? 0 : Math.max(dp(2), (float) (Math.abs((double) amount) * scale));
      float top = amount < 0 ? zero : zero - height;
      float bottom = amount < 0 ? zero + height : zero;
      if (amount == 0) {
        paint.setAlpha(105);
        top -= dp(2);
      }
      canvas.drawRoundRect(x - dp(4), top, x + dp(4), bottom, dp(3), dp(3), paint);
    }
  }

  private final class MonthHolder extends RecyclerView.ViewHolder {
    final MonthCell cell;

    MonthHolder(MonthCell cell) {
      super(cell);
      this.cell = cell;
    }
  }

  private int dp(int value) {
    return Ui.dp(getContext(), value);
  }
}
