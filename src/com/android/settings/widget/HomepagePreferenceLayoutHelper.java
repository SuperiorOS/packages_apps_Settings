/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;
import com.android.settings.flags.Flags;
import com.android.settingslib.widget.SettingsThemeHelper;

/** Helper for homepage preference to manage layout. */
public class HomepagePreferenceLayoutHelper {

    /** Extras key: 0-100 value for the progress bar under the summary, absent to hide it. */
    public static final String EXTRA_PROGRESS = "homepage_progress";

    /** Extras key: 1 = good, 2 = warning, 3 = critical; colors the summary. Absent for none. */
    public static final String EXTRA_SUMMARY_TONE = "homepage_summary_tone";

    // Summary colors by tone; index 0 (no tone) keeps the theme's secondary text color.
    private static final int[] TONE_COLORS = {
            0, R.color.hp_tone_ok, R.color.hp_tone_warn, R.color.hp_tone_bad};

    /**
     * Stores the progress and summary tone on the preference and rebinds its row. The rebind is
     * needed because setting the same summary text again does not notify the adapter, so the
     * progress bar would otherwise never appear.
     */
    public static void setProgress(Preference preference, int progress, int tone) {
        preference.getExtras().putInt(EXTRA_PROGRESS, progress);
        preference.getExtras().putInt(EXTRA_SUMMARY_TONE, tone);
        if (preference instanceof HomepagePreferenceLayout) {
            ((HomepagePreferenceLayout) preference).refreshRow();
        }
    }

    private static final int[] CHIP_BG = {
            R.color.hp_chip1_bg, R.color.hp_chip2_bg, R.color.hp_chip3_bg};
    private static final int[] CHIP_FG = {
            R.color.hp_chip1_fg, R.color.hp_chip2_fg, R.color.hp_chip3_fg};

    private final Preference mPreference;
    private View mIcon;
    private View mText;
    private View mAlertFrame;
    private View mAlertUnnumbered;
    private View mAlertNumberedFrame;
    private TextView mAlertNumberText;
    private boolean mIconVisible = true;
    private int mIconPaddingStart = -1;
    private int mTextPaddingStart = -1;
    private int mAlertValue = -1;

    /** The interface for managing preference layouts on homepage */
    public interface HomepagePreferenceLayout {
        /** Returns a {@link HomepagePreferenceLayoutHelper}  */
        HomepagePreferenceLayoutHelper getHelper();

        /** Rebinds the row, e.g. after the progress or summary tone extras changed. */
        void refreshRow();
    }

    public HomepagePreferenceLayoutHelper(Preference preference) {
        mPreference = preference;
        preference.setLayoutResource(
                SettingsThemeHelper.isExpressiveTheme(preference.getContext())
                        ? R.layout.homepage_preference_expressive
                        : R.layout.homepage_preference);
    }

    /** Sets whether the icon should be visible */
    public void setIconVisible(boolean visible) {
        mIconVisible = visible;
        if (mIcon != null) {
            mIcon.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    /** Sets the icon padding start */
    public void setIconPaddingStart(int paddingStart) {
        mIconPaddingStart = paddingStart;
        if (mIcon != null && paddingStart >= 0) {
            mIcon.setPaddingRelative(paddingStart, mIcon.getPaddingTop(), mIcon.getPaddingEnd(),
                    mIcon.getPaddingBottom());
        }
    }

    /** Sets the text padding start */
    public void setTextPaddingStart(int paddingStart) {
        mTextPaddingStart = paddingStart;
        if (mText != null && paddingStart >= 0) {
            mText.setPaddingRelative(paddingStart, mText.getPaddingTop(), mText.getPaddingEnd(),
                    mText.getPaddingBottom());
        }
    }

    /** Sets the alert value and view */
    public void setAlert(int value) {
        if (Flags.homepageTileAlert()) {
            mAlertValue = value;
            if (mAlertFrame != null && mAlertUnnumbered != null
                    && mAlertNumberedFrame != null && mAlertNumberText != null) {
                mAlertFrame.setVisibility((value > 0) ? View.VISIBLE : View.GONE);
                // only display number if it's single digit, more than 1
                if (value == 1 || value > 9) {
                    mAlertNumberedFrame.setVisibility(View.GONE);
                    mAlertUnnumbered.setVisibility(View.VISIBLE);
                    mAlertFrame.setContentDescription(mAlertFrame.getResources()
                            .getString(R.string.homepage_unnumbered_alert_description));
                } else if (value > 1) {
                    mAlertUnnumbered.setVisibility(View.GONE);
                    mAlertNumberedFrame.setVisibility(View.VISIBLE);
                    mAlertNumberText.setVisibility(View.VISIBLE);
                    mAlertNumberText.setText(String.valueOf(value));
                    mAlertFrame.setContentDescription(mAlertFrame.getResources()
                            .getString(R.string.homepage_numbered_alert_description, value));
                }
            }
        }
    }

    void onBindViewHolder(PreferenceViewHolder holder) {
        mIcon = holder.findViewById(R.id.icon_frame);
        mText = holder.findViewById(R.id.text_frame);
        mAlertFrame = holder.findViewById(R.id.alert_frame);
        mAlertUnnumbered = holder.findViewById(R.id.alert_unnumbered);
        mAlertNumberedFrame = holder.findViewById(R.id.alert_numbered_frame);
        mAlertNumberText = (TextView) holder.findViewById(R.id.alert_number_fg);
        applyIconChip(holder);
        applyProgress(holder);
        applySummaryTone(holder);
        setIconVisible(mIconVisible);
        setIconPaddingStart(mIconPaddingStart);
        setTextPaddingStart(mTextPaddingStart);
        setAlert(mAlertValue);
    }

    /** Shows a thin progress bar under the summary when the preference carries a value. */
    private void applyProgress(PreferenceViewHolder holder) {
        final View v = holder.findViewById(R.id.hp_progress);
        if (!(v instanceof ProgressBar)) {
            return;
        }
        final int progress = mPreference.getExtras().getInt(EXTRA_PROGRESS, -1);
        if (progress < 0) {
            v.setVisibility(View.GONE);
            return;
        }
        ((ProgressBar) v).setProgress(Math.min(progress, 100));
        v.setVisibility(View.VISIBLE);
    }

    /** Colors the summary text green, amber or red when the preference carries a tone. */
    private void applySummaryTone(PreferenceViewHolder holder) {
        final View v = holder.findViewById(android.R.id.summary);
        if (!(v instanceof TextView)) {
            return;
        }
        final TextView summary = (TextView) v;
        final int tone = mPreference.getExtras().getInt(EXTRA_SUMMARY_TONE, 0);
        final Context context = summary.getContext();
        if (tone > 0 && tone < TONE_COLORS.length) {
            summary.setTextColor(context.getColor(TONE_COLORS[tone]));
            return;
        }
        final TypedArray ta = context.obtainStyledAttributes(
                new int[] {android.R.attr.textColorSecondary});
        try {
            summary.setTextColor(ta.getColorStateList(0));
        } finally {
            ta.recycle();
        }
    }

    /** Colors the circular icon chip, cycling the tone by the preference order. */
    private void applyIconChip(PreferenceViewHolder holder) {
        if (mIcon == null) {
            return;
        }
        final int tone = Math.floorMod(mPreference.getOrder(), CHIP_BG.length);
        final Context context = mIcon.getContext();
        mIcon.setBackgroundTintList(ColorStateList.valueOf(context.getColor(CHIP_BG[tone])));
        final View icon = holder.findViewById(android.R.id.icon);
        if (icon instanceof ImageView) {
            final ImageView imageView = (ImageView) icon;
            // The ic_homepage_* icons are layer lists of a colored background shape plus the
            // glyph. The chip supplies the background, so draw only the glyph; otherwise the
            // tint would flatten the whole icon into a solid disc.
            final Drawable current = imageView.getDrawable();
            if (current instanceof LayerDrawable) {
                final LayerDrawable layers = (LayerDrawable) current;
                if (layers.getNumberOfLayers() > 1) {
                    imageView.setImageDrawable(
                            layers.getDrawable(layers.getNumberOfLayers() - 1));
                }
            }
            imageView.setImageTintList(ColorStateList.valueOf(context.getColor(CHIP_FG[tone])));
        }
    }
}
