package com.bydavy.morpher;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;
import com.bydavy.morpher.font.DFont;

public class DigitalClockView extends View {

	public static int DEFAULT_ANIMATION_DURATION = 300;

	public static final Font DEFAULT_FONT = new DFont(170, 10);

	private Font mFont;
	private int mColor;
	private float mThickness;
	private int mFontSize;
	private int mMorphingDurationInMs;

	private float mMorphingPercent;

	// Local variables (opposed to mPreviousChars and mChars that are immutable)
	private float[][] mLocalChars;
	private float[][] mLocalWidth;

	// Store previous values (origin)
	private String mPreviousText;
	private float[][] mPreviousChars;
	private float[][] mPreviousWidth;

	// Store new values (destination)
	private String mText;
	private float[][] mChars;
	private float[][] mWidth;

	// FIXME The column char doesn't fit in my current design
	private boolean[] mIsColumnChar;

	private Paint mPaint;
	private ObjectAnimator mMorphingAnimation;

	public DigitalClockView(Context context) {
		super(context);
		init();
	}

	public DigitalClockView(Context context, AttributeSet attrs) {
		super(context, attrs);
		// TODO Configure view with AttributeSet
		init();
	}

	public DigitalClockView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		// TODO Configure view with AttributeSet
		init();
	}

	private void init() {
		mFont = DEFAULT_FONT;
		mMorphingDurationInMs = DEFAULT_ANIMATION_DURATION;

		mPaint = new Paint();
		mPaint.setColor(Color.argb(255, 0, 255, 0));
		mPaint.setStrokeWidth(3);

		/*setFontColor(Color.argb(255, 255, 255, 255));
		setFontThickness(3);
		setFontSize(100);*/
	}

	public void setTime(String time) {
		setTime(time, true);
	}

	public void setTimeNoAnimation(String time) {
		setTime(time, false);
	}

	private void setTime(String time, boolean shouldMorph) {
		// Update only if time changed
		if (time == mText || (mText != null && mText.equals(time))) return;

		// Changing text length over time is not supported (at least not "morphed")
		if (mText == null || (mPreviousText != null && time != null && (mPreviousText.length() != time.length() || columnCharChangedPosition(mPreviousText, time)))) {
			shouldMorph = false;
		}

		int newSize = time.length();
		int pointsCount = mFont.getPointsCount();

		// Here we might reallocate memory (this view is not designed to frequently changed the time's string length)
		mChars = ArrayHelper.expandIfRequired(mChars, newSize);
		mWidth = ArrayHelper.expandIfRequired(mWidth, newSize);

		mPreviousChars = ArrayHelper.expandIfRequired(mPreviousChars, newSize);
		mPreviousWidth = ArrayHelper.expandIfRequired(mPreviousWidth, newSize);

		mLocalChars = ArrayHelper.expandIfRequired(mLocalChars, newSize, pointsCount);
		mLocalWidth = ArrayHelper.expandIfRequired(mLocalWidth, newSize, pointsCount);

		mIsColumnChar = ArrayHelper.expandIfRequired(mIsColumnChar, newSize);

		// Stage current chars and width in order to generate a morphing animation
		if (shouldMorph) {
			float[][] originChars;
			float[][] originWidths;

			if (!isMorphingAnimationRunning()) {
				originChars = mChars;
				originWidths = mWidth;
			} else {
				// Save current morphing animation accordingly to the current state
				// FIXME Ideally we should not rely on the current mMorphingPercent but rather recompute the percentage based on current time
				int size = mText.length();
				for (int i = 0; i < size; i++) {
					// Save result to mLocalChars and mLocalWidth. Both are local array that can be edited (not the case of
					// mPreviousChars, mPreviousWidth, mChars and mWidth).
					mFont.save(mPreviousChars[i], mChars[i], mMorphingPercent, mLocalChars[i]);
					mFont.saveWidth(mPreviousWidth[i], mWidth[i], mMorphingPercent, mLocalWidth[i]);
				}

				originChars = mLocalChars;
				originWidths = mLocalWidth;
			}
			System.arraycopy(originChars, 0, mPreviousChars, 0, mPreviousChars.length);
			System.arraycopy(originWidths, 0, mPreviousWidth, 0, mPreviousWidth.length);
		}

		fetchGlyphs(time, mChars, mWidth, mIsColumnChar);

		mPreviousText = mText;
		mText = time;

		cancelMorphingAnimation();

		if (shouldMorph) {
			resetMorphingAnimation();
			startMorphingAnimation();
		}

		requestLayout();
		invalidate();
	}

	private void resetMorphingAnimation() {
		mMorphingPercent = 0;
	}

	private void startMorphingAnimation() {
		mMorphingAnimation = ObjectAnimator.ofFloat(this, "morphingPercent", 1);
		mMorphingAnimation.setInterpolator(new LinearInterpolator());
		mMorphingAnimation.setDuration(mMorphingDurationInMs);
		mMorphingAnimation.start();
	}

	private void cancelMorphingAnimation() {
		if (mMorphingAnimation != null) {
			mMorphingAnimation.cancel();
			mMorphingAnimation = null;
		}
	}

	private boolean isMorphingAnimationRunning() {
		return mMorphingAnimation != null && mMorphingAnimation.isRunning();
	}

	private void fetchGlyphs(String text, float[][] chars, float[][] widths, boolean[] isColumnChar) {
		int size = text.length();
		for (int i = 0; i < size; i++) {
			char c = text.charAt(i);

			// FIXME Column doesn't fit in my current impl
			boolean isColumn = c == ':';
			isColumnChar[i] = isColumn;
			if (isColumn) {
				continue;
			}

			if (!mFont.hasGlyph(c)) {
				throw new RuntimeException("Character not supported " + c);
			}
			chars[i] = mFont.getGlyphPoints(c);
			widths[i] = mFont.getGlyphBounds(c);
		}
	}

	public void setMorphingDuration(int durationInMs) {
		mMorphingDurationInMs = durationInMs;
	}

	public void setFont(Font font) {
		//if (mFont != font) {
		mFont = font;

		String time = mText;
		mText = null;
		cancelMorphingAnimation();
		setTime(time);
		//}
	}

	/*public void setFontColor(int color) {
		if (mColor != color) {
			mColor = color;

			invalidate();
		}
	}

	public void setFontSize(int sizeInPx) {
		if (mFontSize != sizeInPx) {
			mFontSize = sizeInPx;

			requestLayout();
			//invalidate();
		}
	}

	public void setFontThickness(int thicknessInPx) {
		if (mThickness != thicknessInPx) {
			mThickness = thicknessInPx;

			requestLayout();
			//invalidate();
		}
	}*/

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		setMinimumWidth(computeMinWidth());
		setMinimumHeight(computeMinHeight());

		int minW = getPaddingLeft() + getSuggestedMinimumWidth() + getPaddingRight();
		int w = resolveSizeAndState(minW, widthMeasureSpec, 1);

		int minH = getPaddingTop() + getSuggestedMinimumHeight() + getPaddingBottom();
		int h = resolveSizeAndState(minH, heightMeasureSpec, 0);

		setMeasuredDimension(w, h);
	}

	private int computeMinWidth() {
		float x = 0;

		if (mChars != null && mWidth != null) {
			float xSeparator = mFont.getGlyphSeparatorWidth();

			int size = mChars.length;
			for (int i = 0; i < size; i++) {
				if (!mIsColumnChar[i]) {
					if (!isMorphingAnimationRunning()) {
						x += mWidth[i][0];
					} else {
						x += mFont.computeWidth(mPreviousWidth[i], mWidth[i], mMorphingPercent);
					}
				} else {
					x += mFont.getColumnWidth();
				}

				if (i < size) {
					x += xSeparator;
				}
			}
		}

		return (int) x;
	}

	private int computeMinHeight() {
		// Only one line supported for now.
		return (int) mFont.getGlyphMaximalBounds()[1];
	}

	@Override
	protected void onDraw(Canvas canvas) {
		float xSeparator = mFont.getGlyphSeparatorWidth() + 2;

		canvas.save();
		canvas.translate(getPaddingLeft(), getPaddingTop());
		if (mChars != null) {
			float charWidth;
			int size = mChars.length;
			for (int i = 0; i < size; i++) {
				if (!mIsColumnChar[i]) {
					if (!isMorphingAnimationRunning()) {
						mFont.draw(canvas, mChars[i]);
						charWidth = mWidth[i][0];
					} else {
						mFont.draw(canvas, mPreviousChars[i], mChars[i], mMorphingPercent);
						charWidth = mFont.computeWidth(mPreviousWidth[i], mWidth[i], mMorphingPercent);
					}
				} else {
					mFont.drawColumn(canvas);
					charWidth = mFont.getColumnWidth();
				}

				canvas.translate(charWidth + xSeparator, 0);
			}

		}
		canvas.restore();
	}

	public void setMorphingPercent(float percent) {
		mMorphingPercent = percent;

		requestLayout();
		invalidate();
	}

	public float getMorphingPercent() {
		return mMorphingPercent;
	}

	private static boolean columnCharChangedPosition(String previousTime, String time) {
		if (previousTime.length() != time.length()) {
			return true;
		}

		final int length = previousTime.length();
		for (int i = 0; i < length; i++) {
			char previousC = previousTime.charAt(i);
			if (previousC == ':' && previousC != time.charAt(i)) {
				return true;
			}
		}

		return false;
	}
}
