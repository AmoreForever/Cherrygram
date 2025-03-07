package uz.unnarsx.cherrygram.ui.tgkit;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.util.ArrayList;

import uz.unnarsx.cherrygram.crashlytics.Crashlytics;
import uz.unnarsx.cherrygram.helpers.AppRestartHelper;
import uz.unnarsx.cherrygram.helpers.BackupHelper;
import uz.unnarsx.cherrygram.ui.tgkit.preference.TGKitCategory;
import uz.unnarsx.cherrygram.ui.tgkit.preference.TGKitPreference;
import uz.unnarsx.cherrygram.ui.tgkit.preference.TGKitSettings;
import uz.unnarsx.cherrygram.utils.PermissionsUtils;
import uz.unnarsx.cherrygram.preferences.BasePreferencesEntry;
import uz.unnarsx.cherrygram.ui.cells.StickerSliderCell;
import uz.unnarsx.cherrygram.ui.tgkit.preference.types.TGKitHeaderRow;
import uz.unnarsx.cherrygram.ui.tgkit.preference.types.TGKitListPreference;
import uz.unnarsx.cherrygram.ui.tgkit.preference.types.TGKitSectionRow;
import uz.unnarsx.cherrygram.ui.tgkit.preference.types.TGKitSettingsCellRow;
import uz.unnarsx.cherrygram.ui.tgkit.preference.types.TGKitSliderPreference;
import uz.unnarsx.cherrygram.ui.tgkit.preference.types.TGKitSwitchPreference;
import uz.unnarsx.cherrygram.ui.tgkit.preference.types.TGKitTextDetailRow;
import uz.unnarsx.cherrygram.ui.tgkit.preference.types.TGKitTextIconRow;
import uz.unnarsx.cherrygram.utils.FileImportActivity;

public class TGKitSettingsFragment extends BaseFragment {
    private final TGKitSettings settings;
    private final SparseArray<TGKitPreference> positions = new SparseArray<>();
    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private int rowCount;

    private final static int report_details = 2;
    private final static int restart_app = 3;
    private final static int backup_settings = 5;
    private final static int restore_settings = 6;

    public TGKitSettingsFragment(BasePreferencesEntry entry) {
        super();
        this.settings = entry.getProcessedPrefs(this);
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        rowCount = 0;
        initSettings();

        return true;
    }

    private void initSettings() {
        for (TGKitCategory category : settings.categories) {
            positions.put(rowCount++, new TGKitHeaderRow(category.name));
            for (TGKitPreference preference : category.preferences) {
                positions.put(rowCount++, preference);
            }
            positions.put(rowCount++, new TGKitSectionRow());
        }
    }

    protected boolean hasWhiteActionBar() {
        return true;
    }

    @Override
    public boolean isLightStatusBar() {
        if (!hasWhiteActionBar()) return super.isLightStatusBar();
        int color = getThemedColor(Theme.key_windowBackgroundWhite);
        return ColorUtils.calculateLuminance(color) > 0.7f;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonDrawable(new BackDrawable(false));

        actionBar.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        actionBar.setItemsColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_actionBarActionModeDefaultSelector), true);
        actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_actionBarWhiteSelector), false);
        actionBar.setItemsColor(getThemedColor(Theme.key_actionBarActionModeDefaultIcon), true);
        actionBar.setTitleColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setCastShadows(false);

        actionBar.setTitle(settings.name);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setOccupyStatusBar(!AndroidUtilities.isTablet());

        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem menuItem = menu.addItem(0, R.drawable.ic_ab_other);
        menuItem.setContentDescription(LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));
        menuItem.addSubItem(report_details, R.drawable.bug_solar, LocaleController.getString("CG_CopyReportDetails", R.string.CG_CopyReportDetails));
        menuItem.addSubItem(restart_app, R.drawable.msg_retry, LocaleController.getString("CG_Restart", R.string.CG_Restart));
        menuItem.addSubItem(backup_settings, R.drawable.msg_openin, LocaleController.getString("CG_ExportSettings", R.string.CG_ExportSettings));
        menuItem.addSubItem(restore_settings, R.drawable.msg_customize, LocaleController.getString("CG_ImportSettings", R.string.CG_ImportSettings));

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == report_details) {
                    AndroidUtilities.addToClipboard(Crashlytics.getReportMessage() + "\n\n#bug");
                    BulletinFactory.of(TGKitSettingsFragment.this).createCopyBulletin(LocaleController.getString("CG_ReportDetailsCopied", R.string.CG_ReportDetailsCopied)).show();
                } else if (id == restart_app) {
                    AppRestartHelper.triggerRebirth(context, new Intent(context, LaunchActivity.class));
                } else if (id == backup_settings) {
                    BackupHelper.backupSettings(context);
                } else if (id == restore_settings) {
                    try {
                        if (Build.VERSION.SDK_INT >= 23 && !PermissionsUtils.isStoragePermissionGranted()) {
                            PermissionsUtils.requestStoragePermission(getParentActivity());
                            return;
                        }
                    } catch (Throwable ignore) {}
                    FileImportActivity fragment = new FileImportActivity(false);
                    fragment.setMaxSelectedFiles(1);
                    fragment.setAllowPhoto(false);
                    fragment.setDelegate(new FileImportActivity.DocumentSelectActivityDelegate() {
                        @Override
                        public void didSelectFiles(FileImportActivity activity, ArrayList<String> files, String caption, boolean notify, int scheduleDate) {
                            activity.finishFragment();
                            BackupHelper.importSettings(getContext(), new File(files.get(0)));
                        }

                        @Override
                        public void didSelectPhotos(ArrayList<SendMessagesHelper.SendingMediaInfo> photos, boolean notify, int scheduleDate) {
                        }

                        @Override
                        public void startDocumentSelectActivity() {
                        }
                    });
                    presentFragment(fragment);
                }
            }
        });

        listAdapter = new ListAdapter(context);

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        listView = new RecyclerListView(context);
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position, x, y) -> {
            TGKitPreference pref = positions.get(position);
            if (pref instanceof TGKitSwitchPreference) {
                ((TGKitSwitchPreference) pref).contract.toggleValue();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(((TGKitSwitchPreference) pref).contract.getPreferenceValue());
                }
            } else if (pref instanceof TGKitTextIconRow) {
                TGKitTextIconRow preference = ((TGKitTextIconRow) pref);
                if (preference.listener != null) preference.listener.onClick(this);
            } else if (pref instanceof TGKitTextDetailRow) {
                TGKitTextDetailRow preference = ((TGKitTextDetailRow) pref);
                if (preference.listener != null) preference.listener.onClick(this);
            } else if (pref instanceof TGKitSettingsCellRow) {
                TGKitSettingsCellRow preference = ((TGKitSettingsCellRow) pref);
                if (preference.listener != null) preference.listener.onClick(this);
            } else if (pref instanceof TGKitListPreference) {
                TGKitListPreference preference = ((TGKitListPreference) pref);
                preference.callActionHueta(this, getParentActivity(), () -> {
                    if (view instanceof TextSettingsCell)
                        ((TextSettingsCell) view).setTextAndValue(preference.title, preference.getContract().getValue(), preference.getDivider());
                });
            }
        });

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class, TextCheckCell.class, HeaderCell.class, NotificationsCheckCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        if (hasWhiteActionBar()) {
            themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
            themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarWhiteSelector));
        } else {
            themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
            themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
            themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
            themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        }

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        return themeDescriptions;
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context mContext;

        ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    break;
                }
                case 1: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    textCell.setCanDisable(false);

                    TGKitSettingsCellRow pref = (TGKitSettingsCellRow) positions.get(position);
                    textCell.setTextColor(pref.textColor);
                    textCell.setText(pref.title, pref.divider);

                    break;
                }
                case 2: {
                    ((HeaderCell) holder.itemView).setText(positions.get(position).title);
                    break;
                }
                case 3: {
                    TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                    TGKitSwitchPreference pref = (TGKitSwitchPreference) positions.get(position);
                    if (pref.description != null) {
                        checkCell.setTextAndValueAndCheck(pref.title, pref.description, pref.contract.getPreferenceValue(), true, pref.divider);
                    } else {
                        checkCell.setTextAndCheck(pref.title, pref.contract.getPreferenceValue(), pref.divider);
                    }
                    break;
                }
                case 4: {
                    TextDetailSettingsCell settingsCell = (TextDetailSettingsCell) holder.itemView;
                    TGKitTextDetailRow pref = (TGKitTextDetailRow) positions.get(position);
                    settingsCell.setMultilineDetail(true);
                    settingsCell.setTextAndValue(pref.title, pref.detail, pref.divider);
                    break;
                }
                case 5: {
                    TextCell cell = (TextCell) holder.itemView;
                    ((TGKitTextIconRow) positions.get(position)).bindCell(cell);
                    break;
                }
                case 6: {
                    ((StickerSliderCell) holder.itemView).setContract(((TGKitSliderPreference) positions.get(position)).contract);
                    break;
                }
                case 7: {
                    TextSettingsCell settingsCell = (TextSettingsCell) holder.itemView;
                    TGKitListPreference pref = (TGKitListPreference) positions.get(position);
                    settingsCell.setTextAndValue(pref.title, pref.getContract().getValue(), pref.getDivider());
                    break;
                }
                case 8: {
                    ((TextInfoPrivacyCell) holder.itemView).setText(positions.get(position).title);
                }
            }
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            int viewType = holder.getItemViewType();
            if (viewType == 3) {
                int position = holder.getAdapterPosition();
                TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                checkCell.setChecked(((TGKitSwitchPreference) positions.get(position)).contract.getPreferenceValue());
            } else if (viewType == 7) {
                int position = holder.getAdapterPosition();
                TextSettingsCell checkCell = (TextSettingsCell) holder.itemView;
                TGKitListPreference pref = ((TGKitListPreference) positions.get(position));
                checkCell.setTextAndValue(pref.title, pref.getContract().getValue(), pref.getDivider());
            }
        }

        public boolean isRowEnabled(int position) {
            return positions.get(position).getType().enabled;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return isRowEnabled(holder.getAdapterPosition());
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case 0:
                    view = new ShadowSectionCell(mContext);
                    break;
                case 1:
                case 7:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 4:
                    view = new TextDetailSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 5:
                    view = new TextCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 6:
                    view = new StickerSliderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 8:
                    view = new TextInfoPrivacyCell(mContext);
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int position) {
            return positions.get(position).getType().adapterType;
        }
    }
}