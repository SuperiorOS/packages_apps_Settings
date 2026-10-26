/*
 * SPDX-FileCopyrightText: The SuperiorOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;

/**
 * A homepage group that shows its title as a small tinted label.
 *
 * <p>The stock untitled category forces its own layout, which hides the title. This one keeps the
 * title and the grouped-card behavior, since the preference adapter groups any category.
 */
public class HomepageGroupCategory extends PreferenceCategory {

    public HomepageGroupCategory(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.homepage_group_category);
    }

    public HomepageGroupCategory(Context context) {
        this(context, null);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.setDividerAllowedAbove(false);
        holder.setDividerAllowedBelow(false);
    }
}
