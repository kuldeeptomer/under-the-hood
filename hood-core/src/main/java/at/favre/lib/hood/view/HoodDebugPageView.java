package at.favre.lib.hood.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.PagerTitleStrip;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import at.favre.lib.hood.R;
import at.favre.lib.hood.page.Page;
import at.favre.lib.hood.page.Pages;

/**
 * The view encapsulating the rendering logic of a {@link Page}. Internally has an recyclerview
 * with a {@link DebugEntriesAdapter}.
 */
public class HoodDebugPageView extends FrameLayout {

    private SwitchableViewpager viewPager;
    private PagerTitleStrip tabs;
    private Pages pages;
    private View progressBarView;
    private
    @ColorInt
    int zebraColor;

    public HoodDebugPageView(Context context) {
        super(context);
        setup();
    }

    public HoodDebugPageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setup();
    }

    public HoodDebugPageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setup();
    }

    private void setup() {
        TypedArray a = getContext().obtainStyledAttributes(new int[]{R.attr.hoodZebraColor});
        zebraColor = a.getColor(0, ContextCompat.getColor(getContext(), android.R.color.transparent));
        a.recycle();

        LayoutInflater.from(getContext()).inflate(R.layout.hoodlib_view_root, this, true);

        viewPager = (SwitchableViewpager) findViewById(R.id.view_pager);
        progressBarView = findViewById(R.id.progress_bar);
        tabs = (PagerTitleStrip) findViewById(R.id.tabs);
    }

    /**
     * Sets the page data (required to for the ui to show anything)
     *
     * @param pages
     */
    public void setPageData(@NonNull Pages pages) {
        this.viewPager.setAdapter(new DebugViewPageAdapter(pages, zebraColor));
        this.pages = pages;
        if (pages.getConfig().autoLog) {
            pages.logPages();
        }

        if (!(getContext() instanceof HoodController)) {
            pages.log("activity does not implement IHoodDebugController - some features might not work");
        }

        if (pages.getAll().size() <= 1 || !pages.getConfig().showPagesIndicator) {
            tabs.setVisibility(GONE);
        } else {
            tabs.setVisibility(VISIBLE);
        }
    }

    public Pages getPages() {
        return pages;
    }

    /**
     * Refreshes all dynamic values
     */
    public void refresh() {
        checkPreconditions();
        viewPager.getAdapter().notifyDataSetChanged();
    }

    /**
     * Sets a progressbar visible/invisible depending on param and blocks UI interactions if is
     * visible
     *
     * @param isVisible
     */
    public void setProgressBarVisible(boolean isVisible) {
        if (progressBarView != null) {
            progressBarView.setVisibility(isVisible ? VISIBLE : GONE);
        }
        viewPager.setPagingEnabled(!isVisible);
    }

    public void addViewPagerChangeListner(ViewPager.OnPageChangeListener listener) {
        checkPreconditions();
        viewPager.addOnPageChangeListener(listener);
    }

    private void checkPreconditions() {
        if (pages == null) {
            throw new IllegalStateException("call setPageData() before using any view features");
        }
    }

}