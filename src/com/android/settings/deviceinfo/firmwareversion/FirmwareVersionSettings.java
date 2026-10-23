/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.settings.deviceinfo.firmwareversion;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.BidiFormatter;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.flags.Flags;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.DeviceInfoUtils;
import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.RestrictedLockUtilsInternal;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.widget.LayoutPreference;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;

@SearchIndexable
public class FirmwareVersionSettings extends DashboardFragment {

    private static final String TAG = "FirmwareVersionSettings";
    private static final String KEY_FIRMWARE_BENTO = "firmware_version_bento";
    private static final int DELAY_TIMER_MILLIS = 500;
    private static final int ACTIVITY_TRIGGER_COUNT = 3;
    private static final List<String> VERSION_NAME_DATE_PATTERNS = Arrays.asList(
            "yyyy-MM-dd", "yyyy-MM");
    private static final Uri SECURITY_URI = Uri.parse(
            "https://source.android.com/docs/security/bulletin/");

    private final long[] mHits = new long[ACTIVITY_TRIGGER_COUNT];
    private UserManager mUserManager;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mUserManager = (UserManager) getContext().getSystemService(Context.USER_SERVICE);
    }

    @Override
    public void onStart() {
        super.onStart();
        setupBentoGrid();
    }

    private void setupBentoGrid() {
        final LayoutPreference bentoPref = findPreference(KEY_FIRMWARE_BENTO);
        if (bentoPref == null) {
            return;
        }

        final Context context = getContext();
        if (context == null) {
            return;
        }

        // 1. Android Version & Easter Egg Trigger
        final View cardAndroid = bentoPref.findViewById(R.id.card_android_version);
        final TextView summaryAndroid = bentoPref.findViewById(R.id.summary_android_version);
        if (summaryAndroid != null) {
            summaryAndroid.setText(Build.VERSION.RELEASE_OR_PREVIEW_DISPLAY);
        }
        if (cardAndroid != null) {
            cardAndroid.setOnClickListener(v -> handleEasterEggClick());
        }

        // 2. Google Play System Update (Mainline Module)
        final View cardModule = bentoPref.findViewById(R.id.card_module_version);
        final TextView summaryModule = bentoPref.findViewById(R.id.summary_module_version);
        setupMainlineModuleCard(context, cardModule, summaryModule);

        // 3. Security Patch
        final View cardSecurity = bentoPref.findViewById(R.id.card_security_patch);
        final TextView summarySecurity = bentoPref.findViewById(R.id.summary_security_patch);
        final String patch = DeviceInfoUtils.getSecurityPatch();
        if (summarySecurity != null) {
            summarySecurity.setText(!TextUtils.isEmpty(patch) ? patch : context.getString(R.string.device_info_default));
        }
        if (cardSecurity != null) {
            cardSecurity.setOnClickListener(v -> {
                final Intent intent = new Intent(Intent.ACTION_VIEW, SECURITY_URI);
                if (!context.getPackageManager().queryIntentActivities(intent, 0).isEmpty()) {
                    context.startActivity(intent);
                }
            });
        }

        // 4. Kernel Version
        final TextView summaryKernel = bentoPref.findViewById(R.id.summary_kernel_version);
        if (summaryKernel != null) {
            summaryKernel.setText(DeviceInfoUtils.getFormattedKernelVersion(context));
        }

        // 5. Baseband Version
        final TextView summaryBaseband = bentoPref.findViewById(R.id.summary_baseband);
        if (summaryBaseband != null) {
            final String baseband = SystemProperties.get("gsm.version.baseband",
                    context.getString(R.string.device_info_default));
            summaryBaseband.setText(baseband);
        }

        // 6. Build Number & Release Type Badge
        final TextView summaryBuild = bentoPref.findViewById(R.id.summary_build_number);
        final TextView badgeBuildType = bentoPref.findViewById(R.id.badge_build_type);
        if (summaryBuild != null) {
            summaryBuild.setText(BidiFormatter.getInstance().unicodeWrap(Build.DISPLAY));
        }
        if (badgeBuildType != null) {
            final String releaseType = SystemProperties.get("ro.superior.releasetype", "COMMUNITY");
            if (!TextUtils.isEmpty(releaseType)) {
                badgeBuildType.setText(releaseType.substring(0, 1).toUpperCase(Locale.ROOT)
                        + releaseType.substring(1).toLowerCase(Locale.ROOT));
            } else {
                badgeBuildType.setText("Community");
            }
        }
    }

    private void setupMainlineModuleCard(Context context, View cardView, TextView summaryView) {
        final PackageManager pm = context.getPackageManager();
        final String moduleProvider = context.getString(
                com.android.internal.R.string.config_defaultModuleMetadataProvider);

        String moduleVersion = null;
        if (!TextUtils.isEmpty(moduleProvider)) {
            try {
                moduleVersion = pm.getPackageInfo(moduleProvider, 0).versionName;
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Failed to get mainline version", e);
            }
        }

        if (summaryView != null) {
            summaryView.setText(formatMainlineDate(moduleVersion));
        }

        if (cardView != null) {
            final Intent v2Intent = new Intent("android.settings.MODULE_UPDATE_VERSIONS");
            final Intent v1Intent = new Intent("android.settings.MODULE_UPDATE_SETTINGS");
            final String packageName = context.getString(
                    com.android.settings.R.string.config_mainline_module_update_package);
            v2Intent.setPackage(packageName);
            v1Intent.setPackage(packageName);

            final ResolveInfo resolvedV2 = pm.resolveActivity(v2Intent, 0);
            final ResolveInfo resolvedV1 = pm.resolveActivity(v1Intent, 0);

            cardView.setOnClickListener(v -> {
                if (resolvedV2 != null) {
                    context.startActivity(v2Intent);
                } else if (resolvedV1 != null) {
                    context.startActivity(v1Intent);
                }
            });
        }
    }

    private CharSequence formatMainlineDate(String version) {
        if (TextUtils.isEmpty(version)) {
            return getContext().getString(R.string.device_info_default);
        }
        for (String pattern : VERSION_NAME_DATE_PATTERNS) {
            try {
                final SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
                sdf.setTimeZone(TimeZone.getDefault());
                final Date date = sdf.parse(version);
                final String format = DateFormat.getBestDateTimePattern(Locale.getDefault(), "dMMMMyyyy");
                return DateFormat.format(format, date);
            } catch (ParseException ignored) {
            }
        }
        return version;
    }

    private void handleEasterEggClick() {
        if (Utils.isMonkeyRunning()) {
            return;
        }
        System.arraycopy(mHits, 1, mHits, 0, mHits.length - 1);
        mHits[mHits.length - 1] = SystemClock.uptimeMillis();
        if (mHits[0] >= (SystemClock.uptimeMillis() - DELAY_TIMER_MILLIS)) {
            if (mUserManager != null && mUserManager.hasUserRestriction(UserManager.DISALLOW_FUN)) {
                final RestrictedLockUtils.EnforcedAdmin admin =
                        RestrictedLockUtilsInternal.checkIfRestrictionEnforced(
                                getContext(), UserManager.DISALLOW_FUN, UserHandle.myUserId());
                if (admin != null) {
                    RestrictedLockUtils.sendShowAdminSupportDetailsIntent(getContext(), admin);
                }
                return;
            }

            final Intent intent = new Intent(Intent.ACTION_MAIN)
                    .setClassName("android", com.android.internal.app.PlatLogoActivity.class.getName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                getContext().startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Unable to start PlatLogoActivity: " + intent, e);
            }
        }
    }

    @Override
    public @Nullable String getPreferenceScreenBindingKey(@NonNull Context context) {
        return null;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.firmware_version;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DIALOG_FIRMWARE_VERSION;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(
                    Flags.catalystSettingsSearch() ? 0 : R.xml.firmware_version);
}
