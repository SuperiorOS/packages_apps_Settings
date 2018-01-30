/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.settings.applications.appinfo;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.os.UserManager;
import android.support.v7.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.TestConfig;
import com.android.settings.testutils.FakeFeatureFactory;
import com.android.settings.testutils.SettingsRobolectricTestRunner;
import com.android.settings.widget.ActionButtonPreference;
import com.android.settings.widget.ActionButtonPreferenceTest;
import com.android.settings.wrapper.DevicePolicyManagerWrapper;
import com.android.settingslib.Utils;
import com.android.settingslib.applications.AppUtils;
import com.android.settingslib.applications.ApplicationsState;
import com.android.settingslib.applications.instantapps.InstantAppDataProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@RunWith(SettingsRobolectricTestRunner.class)
@Config(manifest = TestConfig.MANIFEST_PATH, sdk = TestConfig.SDK_VERSION)
public class AppActionButtonPreferenceControllerTest {

    @Mock
    private UserManager mUserManager;
    @Mock
    private DevicePolicyManagerWrapper mDevicePolicyManager;
    @Mock
    private AppInfoDashboardFragment mFragment;
    @Mock
    private ApplicationInfo mAppInfo;

    private Context mContext;
    private AppActionButtonPreferenceController mController;
    private FakeFeatureFactory mFeatureFactory;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mFeatureFactory = FakeFeatureFactory.setupForTest();
        mContext = spy(RuntimeEnvironment.application);
        mController = spy(new AppActionButtonPreferenceController(mContext, mFragment, "Package1"));
        mController.mActionButtons = ActionButtonPreferenceTest.createMock();
        ReflectionHelpers.setField(mController, "mUserManager", mUserManager);
        ReflectionHelpers.setField(mController, "mDpm", mDevicePolicyManager);
        ReflectionHelpers.setField(mController, "mApplicationFeatureProvider",
                mFeatureFactory.applicationFeatureProvider);
        when(mContext.getSystemService(Context.USER_SERVICE)).thenReturn(mUserManager);
        final PackageInfo packageInfo = mock(PackageInfo.class);
        packageInfo.applicationInfo = mAppInfo;
        when(mFragment.getPackageInfo()).thenReturn(packageInfo);
    }

    @Test
    public void getAvailabilityStatus_notInstantApp_shouldReturnAvailable() {
        ReflectionHelpers.setStaticField(AppUtils.class, "sInstantAppDataProvider",
            (InstantAppDataProvider) (i -> false));

        assertThat(mController.getAvailabilityStatus()).isEqualTo(mController.AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_isInstantApp_shouldReturnDisabled() {
        ReflectionHelpers.setStaticField(AppUtils.class, "sInstantAppDataProvider",
            (InstantAppDataProvider) (i -> true));

        assertThat(mController.getAvailabilityStatus()).isEqualTo(mController.DISABLED_FOR_USER);
    }

    @Test
    public void displayPreference_shouldSetButton2Invisible() {
        final PreferenceScreen screen = mock(PreferenceScreen.class);
        final ActionButtonPreference preference = spy(new ActionButtonPreference(mContext));
        when(screen.findPreference(mController.getPreferenceKey())).thenReturn(preference);

        mController.displayPreference(screen);

        verify(preference).setButton2Visible(false);
    }

    @Test
    public void refreshUi_shouldRefreshButton() {
        final PackageInfo packageInfo = mock(PackageInfo.class);
        final ApplicationsState.AppEntry appEntry = mock(ApplicationsState.AppEntry.class);
        final ApplicationInfo info = new ApplicationInfo();
        appEntry.info = info;
        doNothing().when(mController).initUninstallButtons(appEntry, packageInfo);
        when(mFragment.getAppEntry()).thenReturn(appEntry);
        when(mFragment.getPackageInfo()).thenReturn(packageInfo);

        mController.refreshUi();

        verify(mController).initUninstallButtons(appEntry, packageInfo);
    }

    @Test
    public void initUninstallButtonForUserApp_shouldSetNegativeButton() {
        final ApplicationInfo info = new ApplicationInfo();
        info.flags = ApplicationInfo.FLAG_INSTALLED;
        info.enabled = true;
        final PackageInfo packageInfo = mock(PackageInfo.class);
        packageInfo.applicationInfo = info;
        when(mFragment.getPackageInfo()).thenReturn(packageInfo);

        assertThat(mController.initUninstallButtonForUserApp()).isTrue();
        verify(mController.mActionButtons).setButton1Positive(false);
    }

    // Tests that we don't show the uninstall button for instant apps"
    @Test
    public void initUninstallButtonForUserApp_instantApps_noUninstallButton() {
        // Make this app appear to be instant.
        ReflectionHelpers.setStaticField(AppUtils.class, "sInstantAppDataProvider",
                (InstantAppDataProvider) (i -> true));
        final ApplicationInfo info = new ApplicationInfo();
        info.flags = ApplicationInfo.FLAG_INSTALLED;
        info.enabled = true;
        final ApplicationsState.AppEntry appEntry = mock(ApplicationsState.AppEntry.class);
        appEntry.info = info;
        final PackageInfo packageInfo = mock(PackageInfo.class);
        packageInfo.applicationInfo = info;
        when(mFragment.getPackageInfo()).thenReturn(packageInfo);

        assertThat(mController.initUninstallButtonForUserApp()).isFalse();
        verify(mController.mActionButtons).setButton1Visible(false);
    }

    @Test
    public void initUninstallButtonForUserApp_notInstalledForCurrentUser_shouldDisableButton() {
        final ApplicationInfo info = new ApplicationInfo();
        info.enabled = true;
        final PackageInfo packageInfo = mock(PackageInfo.class);
        packageInfo.applicationInfo = info;
        when(mFragment.getPackageInfo()).thenReturn(packageInfo);
        final int userID1 = 1;
        final int userID2 = 2;
        final List<UserInfo> userInfos = new ArrayList<>();
        userInfos.add(new UserInfo(userID1, "User1", UserInfo.FLAG_PRIMARY));
        userInfos.add(new UserInfo(userID2, "User2", UserInfo.FLAG_GUEST));
        when(mUserManager.getUsers(true)).thenReturn(userInfos);

        assertThat(mController.initUninstallButtonForUserApp()).isFalse();
    }

    @Test
    public void handleDisableable_appIsHomeApp_buttonShouldNotWork() {
        final ApplicationInfo info = new ApplicationInfo();
        info.packageName = "pkg";
        info.enabled = true;
        final ApplicationsState.AppEntry appEntry = mock(ApplicationsState.AppEntry.class);
        appEntry.info = info;
        final HashSet<String> homePackages = new HashSet<>();
        homePackages.add(info.packageName);
        ReflectionHelpers.setField(mController, "mHomePackages", homePackages);

        assertThat(mController.handleDisableable(appEntry, mock(PackageInfo.class))).isFalse();
        verify(mController.mActionButtons).setButton1Text(R.string.disable_text);
    }

    @Test
    @Config(shadows = ShadowUtils.class)
    public void handleDisableable_appIsEnabled_buttonShouldWork() {
        final ApplicationInfo info = new ApplicationInfo();
        info.packageName = "pkg";
        info.enabled = true;
        info.enabledSetting = PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        final ApplicationsState.AppEntry appEntry = mock(ApplicationsState.AppEntry.class);
        appEntry.info = info;
        when(mFeatureFactory.applicationFeatureProvider.getKeepEnabledPackages())
                .thenReturn(new HashSet<>());

        assertThat(mController.handleDisableable(appEntry, mock(PackageInfo.class))).isTrue();
        verify(mController.mActionButtons).setButton1Text(R.string.disable_text);
    }

    @Test
    @Config(shadows = ShadowUtils.class)
    public void handleDisableable_appIsDisabled_buttonShouldShowEnable() {
        final ApplicationInfo info = new ApplicationInfo();
        info.packageName = "pkg";
        info.enabled = false;
        info.enabledSetting = PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        final ApplicationsState.AppEntry appEntry = mock(ApplicationsState.AppEntry.class);
        appEntry.info = info;
        when(mFeatureFactory.applicationFeatureProvider.getKeepEnabledPackages())
                .thenReturn(new HashSet<>());

        assertThat(mController.handleDisableable(appEntry, mock(PackageInfo.class))).isTrue();
        verify(mController.mActionButtons).setButton1Text(R.string.enable_text);
        verify(mController.mActionButtons).setButton1Positive(true);
    }

    @Test
    @Config(shadows = ShadowUtils.class)
    public void handleDisableable_appIsEnabledAndInKeepEnabledWhitelist_buttonShouldNotWork() {
        final ApplicationInfo info = new ApplicationInfo();
        info.packageName = "pkg";
        info.enabled = true;
        info.enabledSetting = PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        final ApplicationsState.AppEntry appEntry = mock(ApplicationsState.AppEntry.class);
        appEntry.info = info;
        final HashSet<String> packages = new HashSet<>();
        packages.add(info.packageName);
        when(mFeatureFactory.applicationFeatureProvider.getKeepEnabledPackages())
                .thenReturn(packages);

        assertThat(mController.handleDisableable(appEntry, mock(PackageInfo.class))).isFalse();
        verify(mController.mActionButtons).setButton1Text(R.string.disable_text);
    }

    @Implements(Utils.class)
    public static class ShadowUtils {
        @Implementation
        public static boolean isSystemPackage(Resources resources, PackageManager pm,
                PackageInfo pkg) {
            return false;
        }
    }

}
